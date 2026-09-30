package moe.kirakira.data.settings

import java.util.Locale
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
internal data class TagName(val name: String, val default: Boolean, val original: Boolean)
internal data class TagLanguage(val language: String, val names: List<TagName>)
internal data class RuleTag(val id: Long, val languages: List<TagLanguage>) {
    fun displayName(language: String): String {
        val locale = Locale.forLanguageTag(language)
        val cerasusLanguage = when (locale.language) {
            "zh" -> if (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) "zht" else "zhs"
            else -> locale.language
        }
        val names = (languages.find { it.language == cerasusLanguage }
            ?: languages.find { it.language.equals(language, ignoreCase = true) }
            ?: languages.find { it.language == "other" } ?: languages.firstOrNull())?.names.orEmpty()
        return names.find { it.default && it.name.isNotBlank() }?.name
            ?: names.firstOrNull { it.name.isNotBlank() }?.name ?: "#$id"
    }

    fun originalName(): String? = languages.flatMap { it.names }.lastOrNull { it.original }?.name
}

internal data class Invitation(val code: String, val createdAt: Long, val used: Boolean)
internal sealed interface InvitationCreation {
    data class Created(val invitation: Invitation) : InvitationCreation
    data object CoolingDown : InvitationCreation
}
internal class UnsafeRuleException : Exception()
