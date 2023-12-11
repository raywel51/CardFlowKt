package th.co.octagon.interactive.android_lab_card_receiver_dispenser

enum class Action(val value: String) {
    CHECK_CONNECTOR("F200000343303303B2"), /* Check Card Connection */
    SENT_TO_TRAY("F200000343323203B1"), /* Sent Card To Read Tray */
    CHECK_CARD("F200000343323203B1"), /* Check Status Card Already */
    CARD_TRASH("F200000343323303B0"), /* Move Card To Card Trash Tray */
    READ("F2000005436030414203E4"), /* Read Data S/n From Card */
    CARD_RECEIVE("F200000343323003B3") /* Sent Card To Front */
}