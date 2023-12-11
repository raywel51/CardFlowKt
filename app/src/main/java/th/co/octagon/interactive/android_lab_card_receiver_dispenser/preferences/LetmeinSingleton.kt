package th.co.octagon.interactive.android_lab_card_receiver_dispenser.preferences

object LetmeinSingleton {

    private var data: String? = null

    fun getData(): String {
        return data ?: ""
    }

    fun setData(newData: String) {
        data = newData
    }
}
