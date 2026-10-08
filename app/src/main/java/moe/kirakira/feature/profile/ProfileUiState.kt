package moe.kirakira.feature.profile

import moe.kirakira.data.content.PublicProfile

internal data class ProfileUiState(
    val profile: PublicProfile,
    val followingCount: Int? = null,
    val followerCount: Int? = null,
    val busy: Boolean = false,
    val avatarKey: String? = null,
) {
    val isSelf get() = profile.isSelf
    val following get() = profile.following
}
