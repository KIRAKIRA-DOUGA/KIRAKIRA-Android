package moe.kirakira.feature.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.util.Locale
import moe.kirakira.R
import moe.kirakira.core.system.AppLanguage
import moe.kirakira.core.system.applyAppLanguage
import moe.kirakira.core.system.readAppLanguage
import moe.kirakira.core.system.systemLocale
import moe.kirakira.feature.settings.search.settingsSearchAnchor
import moe.kirakira.ui.theme.KIRAKIRATheme

private data class LanguageOption(
    val language: AppLanguage,
    @param:StringRes val nativeName: Int,
    @param:StringRes val localizedName: Int,
)

private val languageOptions = listOf(
    LanguageOption(AppLanguage.ENGLISH, R.string.language_native_english, R.string.language_english),
    LanguageOption(AppLanguage.CHINESE, R.string.language_native_chinese_simplified, R.string.language_chinese_simplified),
    LanguageOption(
        AppLanguage.CHINESE_TRADITIONAL,
        R.string.language_native_chinese_traditional,
        R.string.language_chinese_traditional,
    ),
    LanguageOption(AppLanguage.JAPANESE, R.string.language_native_japanese, R.string.language_japanese),
)

@Composable
internal fun LanguageSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val inspection = LocalInspectionMode.current
    val locales = LocalConfiguration.current.locales
    var localeRevision by remember { mutableIntStateOf(0) }
    if (!inspection) {
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { localeRevision++ }
        val application = context.applicationContext
        // The explicit app selection can change even when the effective locales stay the same.
        DisposableEffect(application) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) { localeRevision++ }
            }
            ContextCompat.registerReceiver(
                application,
                receiver,
                IntentFilter(Intent.ACTION_LOCALE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            onDispose { application.unregisterReceiver(receiver) }
        }
    }
    val selected = remember(context, locales, localeRevision, inspection) {
        if (inspection) AppLanguage.SYSTEM else readAppLanguage(context)
    }
    val system = remember(context, locales, localeRevision, inspection) {
        if (inspection) Locale.getDefault() else systemLocale(context)
    }
    SettingsPage(
        title = stringResource(R.string.settings_language),
        onBack = onBack,
        shadingIcon = R.drawable.ic_icon_translate,
        modifier = modifier,
    ) {
        LanguageSettingsContent(
            selected = selected,
            systemLanguageName = system.getDisplayName(system).replaceFirstChar { it.titlecase(system) },
            onSelect = { language ->
                activity?.let {
                    applyAppLanguage(it, language)
                    localeRevision++
                }
            },
        )
    }
}

@Composable
private fun LanguageSettingsContent(
    selected: AppLanguage,
    systemLanguageName: String,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(SettingsDefaults.SectionSpacing),
    ) {
        SettingsSection {
            SettingsRadioItem(
                title = stringResource(R.string.language_system),
                supporting = stringResource(R.string.language_system_current, systemLanguageName),
                selected = selected == AppLanguage.SYSTEM,
                onClick = { onSelect(AppLanguage.SYSTEM) },
                index = 0,
                count = 1,
                icon = R.drawable.ic_symbol_android,
            )
        }
        SettingsSection(title = stringResource(R.string.language_section_languages)) {
            languageOptions.forEachIndexed { index, option ->
                val nativeName = stringResource(option.nativeName)
                val localizedName = stringResource(option.localizedName)
                // Each language keeps its own name on one line, so rows never change height when the
                // interface language changes; the localized name only serves settings search.
                SettingsRadioItem(
                    title = nativeName,
                    selected = selected == option.language,
                    onClick = { onSelect(option.language) },
                    index = index,
                    count = languageOptions.size,
                    modifier = Modifier.settingsSearchAnchor(localizedName),
                )
            }
        }
    }
}

@Preview(name = "Language · English", locale = "en", showBackground = true)
@Preview(name = "语言 · 中文", locale = "zh", showBackground = true)
@Preview(name = "言語 · 日本語", locale = "ja", showBackground = true)
@Composable
private fun LanguageSettingsPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        LanguageSettingsScreen(onBack = {})
    }
}
