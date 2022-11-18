package link.cure.recorder.utils

fun String.toPhoneString(): String {
    return if (this.length == 13) {
        this
    } else if (this.length == 10) {
        "+91$this"
    } else if (this.length == 12 && this[0] == '9') {
        "+$this"
    } else {
        this
    }
}