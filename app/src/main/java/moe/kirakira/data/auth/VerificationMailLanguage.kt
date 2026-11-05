package moe.kirakira.data.auth

import java.util.Locale

/** Rosales currently supports these two verification-email languages. */
internal fun verificationMailLanguage(locale: Locale): String =
    if (locale.language == "zh") "zh-Hans-CN" else "en-US"
