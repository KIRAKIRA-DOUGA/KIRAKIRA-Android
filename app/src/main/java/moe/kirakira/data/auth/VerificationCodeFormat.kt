package moe.kirakira.data.auth

/** Shared protocol formats; filtering input never submits or truncates a code. */
internal enum class VerificationCodeFormat(val length: Int, private val allowsLetters: Boolean) {
    NUMERIC(6, false),
    BACKUP(6, true),
    RECOVERY(24, true);

    fun accepts(character: Char): Boolean = character in '0'..'9' || (allowsLetters && character in 'A'..'Z')

    fun isValid(value: String): Boolean = value.length == length && value.all(::accepts)

    fun filterInput(value: String): String = value.uppercase().filter(::accepts)
}
