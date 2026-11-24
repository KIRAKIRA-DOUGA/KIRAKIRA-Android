package moe.kirakira.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import moe.kirakira.data.content.FollowListKind
import moe.kirakira.feature.gallery.GalleryComponent
import moe.kirakira.feature.imageviewer.ViewerImage
import java.util.UUID

@Serializable
internal data object MainRoute : NavKey

@Serializable
internal data object HistoryRoute : NavKey

@Serializable
internal data object FavoritesRoute : NavKey

@Serializable
internal data object SettingsRoute : NavKey

@Serializable
internal data object ProfileEditorRoute : NavKey

@Serializable
internal data object PrivacySettingsRoute : NavKey

@Serializable
internal data object SecuritySettingsRoute : NavKey

@Serializable
internal data object AboutRoute : NavKey

@Serializable
internal data object ComponentGalleryRoute : NavKey

@Serializable
internal data class ComponentDetailRoute(val component: GalleryComponent) : NavKey

@Serializable
internal data object LicensesRoute : NavKey

@Serializable
internal data object AppearanceRoute : NavKey

@Serializable
internal data object AccountSwitchRoute : NavKey

@Serializable
internal data class VideoRoute(val videoId: Int) : NavKey

@Serializable
internal data class TagRoute(val tagId: Long) : NavKey

@Serializable
internal data class ProfileRoute(val uid: Long) : NavKey

@Serializable
internal data class FollowListRoute(val uid: Long, val kind: FollowListKind) : NavKey

@Serializable
internal data object SelfProfileRoute : NavKey

@Serializable
internal data class ImageViewerRoute(
    val image: ViewerImage,
    val instanceId: String = UUID.randomUUID().toString(),
) : NavKey

@Serializable
internal data object DanmakuSettingsRoute : NavKey

@Serializable
internal data object PlaybackSettingsRoute : NavKey

@Serializable
internal data object LanguageSettingsRoute : NavKey

@Serializable
internal data object BlockingOverviewRoute : NavKey

@Serializable
internal data class RuleManagementRoute(val category: moe.kirakira.data.settings.RuleCategory) : NavKey

@Serializable
internal data object InvitationsRoute : NavKey

/** Static settings directory identity; no account or form values enter this route. */
@Serializable
internal data class SettingsSearchTargetRoute(val entryId: String) : NavKey
