package moe.kirakira.data.auth

private val sessionUuidPattern = Regex("[a-zA-Z0-9-]+")
private val bootstrapHintPattern = Regex("[a-zA-Z0-9_-]+")

internal fun isValidSessionUuid(value: String): Boolean = sessionUuidPattern.matches(value)

internal fun isValidSessionToken(value: String): Boolean =
    value.isNotBlank() && value.all { it.code in 0x21..0x7E && it != ';' && it != ',' }

internal fun isValidBootstrapHint(value: String): Boolean = bootstrapHintPattern.matches(value)
