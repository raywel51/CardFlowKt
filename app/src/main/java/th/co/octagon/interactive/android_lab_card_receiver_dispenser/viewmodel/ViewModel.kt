package th.co.octagon.interactive.android_lab_card_receiver_dispenser.viewmodel

import android.annotation.SuppressLint
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import th.co.octagon.interactive.android_lab_card_receiver_dispenser.MainActivity

class MyViewModel : ViewModel() {

    @SuppressLint("StaticFieldLeak")
    var mainActivityInstance: MainActivity? = null

    val textValue = mutableStateOf("")

    suspend fun tryToRunCode() {
        return withContext(Dispatchers.IO) {
            mainActivityInstance?.tryToRunCode()
        }
    }
}
