package moe.kirakira.data.auth

import java.util.Locale

/**
 * Rosales has complete verification-email templates in English, Simplified Chinese and Japanese.
 * Its Traditional Chinese templates are still empty and make the send fail, so all Chinese uses Simplified.
 */
internal fun verificationMailLanguage(locale: Locale): String = when (locale.language) {
    "zh" -> "zh-Hans-CN"
    "ja" -> "ja"
    else -> "en-US"
}
