package th.co.octagon.interactive.android_lab_card_receiver_dispenser

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import th.co.octagon.interactive.android_lab_card_receiver_dispenser.ui.theme.Android_lab_card_receiver_dispenserTheme
import th.co.octagon.interactive.android_lab_card_receiver_dispenser.viewmodel.MyViewModel
import java.io.IOException
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel = MyViewModel()

    object ArduinoConnectionManager {
        const val actionArduinoPermission = "th.co.octagoninteractive.letmein.ballotbox.ACTION_CARD_USB_PERMISSION"
        const val usbPermissionRequestCode = 100

        var desiredDevice: UsbSerialDriver? = null
        var usbConnection: UsbDeviceConnection? = null
        var usbPort: UsbSerialPort? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.mainActivityInstance = this

        setContent {
            DispenserView(viewModel = viewModel)
        }

        connectSerial()
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun connectSerial() {
        val manager = getSystemService(USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) {
            println("not found availableDrivers")
            return
        }

        val desiredVendorIds: IntArray = resources.getIntArray(R.array.crt)
        for (driver in availableDrivers) {
            println("found usb id ${driver.device.vendorId}")
            if (desiredVendorIds.contains(driver.device.vendorId)) {
                ArduinoConnectionManager.desiredDevice = driver
                break
            }
        }

        if (ArduinoConnectionManager.desiredDevice == null) {
            println("not found desiredDevice")
            return
        }

        val permissionIntent = PendingIntent.getBroadcast(this, ArduinoConnectionManager.usbPermissionRequestCode, Intent(ArduinoConnectionManager.actionArduinoPermission), 0)
        manager.requestPermission(ArduinoConnectionManager.desiredDevice?.device, permissionIntent)

        connectToDevice()
    }

    @Deprecated("This method uses a deprecated super method", level = DeprecationLevel.WARNING)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == ArduinoConnectionManager.usbPermissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                connectToDevice()
            } else {
                // Handle the case where permission is not granted
            }
        }
    }

    private fun connectToDevice() {

        val manager = getSystemService(USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) {
            return
        }

        var desiredDevice: UsbSerialDriver? = null

        val desiredVendorIds: IntArray = resources.getIntArray(R.array.crt)
        for (driver in availableDrivers) {
            println("found usb id ${driver.device.vendorId}")
            if (desiredVendorIds.contains(driver.device.vendorId)) {
                desiredDevice = driver
                break
            }
        }


        desiredDevice?.let { driver ->
            val connection = manager.openDevice(driver.device) ?: return

            ArduinoConnectionManager.usbConnection = connection
            val port = driver.ports[0]

            try {
                port.open(connection)
                port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                ArduinoConnectionManager.usbPort = port
                Log.v("USB SERIAL","Connection successful!")
            } catch (e: IOException) {
                e.printStackTrace()
                println("Connection failed!")
            }
        }
    }

    private suspend fun writeToDevice(data: Action) {

        fun convertToBytes(data : String):ByteArray {
            return data.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }

        ArduinoConnectionManager.usbPort?.let { port ->
            try {
                port.write(convertToBytes(data.value), 1000)
                delay(100) 
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    suspend fun tryToRunCode(): String? {
        return runBlocking {
            var sn: String? = null

            writeToDevice(Action.CHECK_CONNECTOR)
            writeToDevice(Action.SENT_TO_TRAY)
            writeToDevice(Action.CHECK_CARD)

            for (counter in 10 downTo 0) {
                if (counter == 0) {
                    writeToDevice(Action.CARD_TRASH)
                } else {
                    writeToDevice(Action.READ)
                    delay(200)
                    val result = readData()

                    if (result?.length == 44) {
                        sn = result.substring(30, result.length - 6)
                        println("4 - > $sn${10 - counter}")
                        viewModel.textValue.value = sn
                        break
                    }
                }
            }
            sn // Return the obtained serial number
        }
    }


    private fun readData(): String? {
        val readWaitWillis = 100

        val response = ByteArray(1024)

        try {
            val len: Int? = ArduinoConnectionManager.usbPort?.read(response, readWaitWillis)
            if (len != null) {
                if (len > 0) {
                    val responseData = response.copyOf(len)
                    return responseData.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase(
                        Locale.ROOT) }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }

        return null
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispenserView(viewModel: MyViewModel) {
    var buttonEnabled by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(buttonEnabled) {
        if (!buttonEnabled) {
            delay(1000) // 5000 milliseconds = 5 seconds
            buttonEnabled = true
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Title Bar
        Text(
            text = "App Title",
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium // Adjust text style as needed
        )

        // Existing Views
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
                .background(MaterialTheme.colorScheme.background),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .width(299.dp)
                    .padding(start = 10.dp, end = 10.dp)
            ) {
                Text(
                    text = "Hello Android!",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    textAlign = TextAlign.Center
                )
                TextField(
                    value = viewModel.textValue.value,
                    onValueChange = { viewModel.textValue.value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(top = 10.dp),
                    label = { Text("Enter text") }
                )

                FilledTonalButton(
                    onClick = {
                        CoroutineScope(Dispatchers.Main).launch {
                            // Assuming mainActivityInstance is your reference to MainActivity
                            viewModel.tryToRunCode()
                        }
                    },
                    enabled = buttonEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        contentColor = Color.White,
                        containerColor = Color.Magenta
                    )
                ) {
                    Text(text = "OK")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainPreview() {
    Android_lab_card_receiver_dispenserTheme {
        DispenserView(viewModel = MyViewModel()) // Provide a ViewModel instance here
    }
}
