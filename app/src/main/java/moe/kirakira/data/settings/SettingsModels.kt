package moe.kirakira.data.settings

import kotlinx.serialization.Serializable

@Serializable
internal enum class RuleCategory(val wireName: String, val endpoint: String, val field: String) {
    BLOCK("block", "user", "blockUid"),
    HIDE("hide", "hideuser", "hideUid"),
    TAG("tag", "tag", "tagId"),
    KEYWORD("keyword", "keyword", "blockKeyword"),
    REGEX("regex", "regex", "blockRegex"),
}

internal data class RuleEntry(
    val value: String,
    val createdAt: Long,
    val uid: Long? = null,
    val name: String? = null,
    val avatar: String? = null,
    val tag: RuleTag? = null,
)

internal data class RulePage(val entries: List<RuleEntry>, val total: Int, val page: Int)
internal typealias RuleTag = moe.kirakira.data.content.VideoTag

internal data class Invitation(val code: String, val createdAt: Long, val used: Boolean)
internal sealed interface InvitationCreation {
    data class Created(val invitation: Invitation) : InvitationCreation
    data object CoolingDown : InvitationCreation
}
internal class UnsafeRuleException : Exception()

internal enum class PrivacyItem(val wireName: String) {
    BIRTHDAY("privary.birthday"),
    AGE("privary.age"),
    FOLLOWING("privary.follow"),
    FOLLOWERS("privary.fans"),
    FAVORITES("privary.favorites"),
}

internal enum class PrivacyVisibility(val wireName: String) {
    PUBLIC("public"), FOLLOWING("following"), PRIVATE("private"),
}

internal data class PrivacyEntry(val id: String, val visibility: PrivacyVisibility)
internal data class LinkedPrivacyEntry(val platformId: String, val visibility: PrivacyVisibility)
internal data class PrivacySettings(
    val entries: List<PrivacyEntry>,
    val linkedAccounts: List<LinkedPrivacyEntry>,
) {
    val values: Map<PrivacyItem, PrivacyVisibility>
        get() = PrivacyItem.entries.associateWith { item ->
            entries.find { it.id == item.wireName }?.visibility ?: PrivacyVisibility.PUBLIC
        }
}
