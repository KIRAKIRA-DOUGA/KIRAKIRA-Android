package moe.kirakira.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import moe.kirakira.data.content.FollowListKind
import moe.kirakira.feature.imageviewer.ViewerImage
import java.util.UUID

// Preserve the serialized route names when moving routes into the navigation package.
@Serializable
@SerialName("moe.kirakira.MainRoute")
internal data object MainRoute : NavKey

@Serializable
internal data object HistoryRoute : NavKey

@Serializable
@SerialName("moe.kirakira.SettingsRoute")
internal data object SettingsRoute : NavKey

@Serializable
internal data object ProfileEditorRoute : NavKey

@Serializable
internal data object PrivacySettingsRoute : NavKey

@Serializable
internal data object SecuritySettingsRoute : NavKey

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

// Decode legacy saved stacks so AppNavHost can discard the retired demo destination.
@Serializable
@SerialName("moe.kirakira.TestRoute")
internal data object TestRoute : NavKey

@Serializable
internal data class VideoRoute(val videoId: Int = -1) : NavKey

@Serializable
internal data class TagRoute(val tagId: Long) : NavKey

@Serializable
internal data class ProfileRoute(val uid: Long = -1) : NavKey

@Serializable
internal data class FollowListRoute(val uid: Long, val kind: FollowListKind) : NavKey

@Serializable
internal data object SelfProfileRoute : NavKey

@Serializable
internal data class ImageViewerRoute(
    val image: ViewerImage,
    // A default also allows restoring stacks saved before viewer instances had an identity.
    val instanceId: String = UUID.randomUUID().toString(),
) : NavKey

@Serializable
internal data object DanmakuSettingsRoute : NavKey

@Serializable
internal data object PlaybackSettingsRoute : NavKey

@Serializable
internal data object BlockingOverviewRoute : NavKey

@Serializable
internal data class RuleManagementRoute(val category: moe.kirakira.data.settings.RuleCategory) : NavKey

@Serializable
internal data object InvitationsRoute : NavKey
