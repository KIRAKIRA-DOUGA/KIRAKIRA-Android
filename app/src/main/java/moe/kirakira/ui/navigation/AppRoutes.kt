package moe.kirakira.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Preserve the serialized route names when moving routes into the navigation package.
@Serializable
@SerialName("moe.kirakira.MainRoute")
internal data object MainRoute : NavKey

@Serializable
@SerialName("moe.kirakira.SettingsRoute")
internal data object SettingsRoute : NavKey

@Serializable
@SerialName("moe.kirakira.AboutRoute")
internal data object AboutRoute : NavKey

@Serializable
@SerialName("moe.kirakira.LicensesRoute")
internal data object LicensesRoute : NavKey

@Serializable
@SerialName("moe.kirakira.AppearanceRoute")
internal data object AppearanceRoute : NavKey

@Serializable
@SerialName("moe.kirakira.AccountSwitchRoute")
internal data object AccountSwitchRoute : NavKey

@Serializable
@SerialName("moe.kirakira.TestRoute")
internal data object TestRoute : NavKey

@Serializable
internal data object VideoRoute : NavKey
