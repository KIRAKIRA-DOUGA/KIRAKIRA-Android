package moe.kirakira.core.system

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/** In-app language choice. [SYSTEM] keeps following the device language list. */
internal enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    CHINESE("zh"),
    CHINESE_TRADITIONAL("zh-Hant"),
    JAPANESE("ja"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage {
            val locale = tag?.takeIf { it.isNotBlank() }?.let(Locale::forLanguageTag) ?: return SYSTEM
            return when (locale.language) {
                "en" -> ENGLISH
                "ja" -> JAPANESE
                "zh" -> if (locale.isTraditionalChinese()) CHINESE_TRADITIONAL else CHINESE
                else -> SYSTEM
            }
        }
    }
}

/** Traditional script, or a Traditional-script region when the script is unspecified. */
private fun Locale.isTraditionalChinese(): Boolean = when (script) {
    "Hant" -> true
    "Hans" -> false
    else -> country in setOf("TW", "HK", "MO")
}

private const val LanguagePreferences = "kirakira_settings"
private const val LanguageKey = "app_language"

/** Language stored before Android 13; Compose reads it to relocalize in place. */
private val legacyLanguage = mutableStateOf(AppLanguage.SYSTEM)

/**
 * Android 13+ stores the choice as the system per-app language, so it also appears in system settings.
 * Older versions keep the tag locally and apply it through [initializeLegacyAppLanguage] and [ProvideAppLanguage].
 */
internal fun readAppLanguage(context: Context): AppLanguage =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        AppLanguage.fromTag(if (locales.isEmpty) null else locales[0].toLanguageTag())
    } else {
        legacyLanguage.value
    }

/**
 * Applies [language] without recreating the Activity, which would flash an empty window.
 * Android 13+ delivers it as a handled `locale|layoutDirection` configuration change; older versions
 * update the shared resources and let [ProvideAppLanguage] relocalize the composition.
 */
internal fun applyAppLanguage(activity: Activity, language: AppLanguage) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val manager = activity.getSystemService(LocaleManager::class.java)
        val locales = language.tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
        if (manager.applicationLocales != locales) manager.applicationLocales = locales
    } else {
        if (legacyLanguage.value == language) return
        legacyPreferences(activity).edit().apply {
            if (language.tag == null) remove(LanguageKey) else putString(LanguageKey, language.tag)
        }.apply()
        legacyLanguage.value = language
        activity.updateLegacyLocaleResources(language.locales())
    }
}

/** The device's preferred locale, independent of the in-app choice. */
internal fun systemLocale(context: Context): Locale =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java).systemLocales.get(0)
    } else {
        Resources.getSystem().configuration.locales.get(0)
    } ?: Locale.getDefault()

/** Loads the local choice before the first composition on Android 12 and earlier. */
internal fun initializeLegacyAppLanguage(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
    legacyLanguage.value = AppLanguage.fromTag(legacyPreferences(activity).getString(LanguageKey, null))
    reapplyLegacyAppLanguage(activity)
}

/** Configuration changes can replace the resource locales. Only a manual choice needs reapplying. */
internal fun reapplyLegacyAppLanguage(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
    val language = legacyLanguage.value
    if (language != AppLanguage.SYSTEM) activity.updateLegacyLocaleResources(language.locales())
}

/**
 * Before Android 13, keeps the composition in the selected language after an in-place switch and across
 * the configuration changes the Activity handles itself, such as rotation. A no-op on Android 13+.
 */
@Composable
internal fun ProvideAppLanguage(content: @Composable () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        content()
        return
    }
    // Always wrap on older versions: switching between wrapped and unwrapped would reset the app's state.
    val base = LocalConfiguration.current
    val locales = legacyLanguage.value.locales()
    val context = LocalContext.current
    val configuration = remember(base, locales) { Configuration(base).apply { setLocales(locales) } }
    val localized = remember(context, configuration) {
        LocalizedContext(context, context.createConfigurationContext(configuration).resources)
    }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides configuration,
        LocalLayoutDirection provides
            if (configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl
            else LayoutDirection.Ltr,
        content = content,
    )
}

/** Keeps the Activity reachable through `baseContext` while serving localized resources. */
private class LocalizedContext(base: Context, private val localized: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = localized
}

private fun AppLanguage.locales(): LocaleList =
    tag?.let(LocaleList::forLanguageTags) ?: Resources.getSystem().configuration.locales

private fun Activity.updateLegacyLocaleResources(locales: LocaleList) {
    LocaleList.setDefault(locales)
    resources.setLocales(locales)
    applicationContext.resources.setLocales(locales)
}

private fun Resources.setLocales(locales: LocaleList) {
    if (configuration.locales == locales) return
    val configuration = Configuration(configuration).apply { setLocales(locales) }
    @Suppress("DEPRECATION")
    updateConfiguration(configuration, displayMetrics)
}

private fun legacyPreferences(context: Context) =
    context.getSharedPreferences(LanguagePreferences, Context.MODE_PRIVATE)
