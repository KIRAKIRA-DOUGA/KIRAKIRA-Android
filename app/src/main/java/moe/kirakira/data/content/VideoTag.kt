package moe.kirakira.data.content

import java.util.Locale
import kotlinx.serialization.Serializable
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure

internal data class TagName(val name: String, val default: Boolean, val original: Boolean)
internal data class TagLanguage(val language: String, val names: List<TagName>)
internal data class VideoTag(val id: Long, val languages: List<TagLanguage>) {
    fun displayName(language: String): String {
        val locale = Locale.forLanguageTag(language)
        val wireLanguage = when (locale.language) {
            "zh" -> if (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) "zht" else "zhs"
            else -> locale.language
        }
        val names = (languages.find { it.language == wireLanguage }
            ?: languages.find { it.language.equals(language, ignoreCase = true) }
            ?: languages.find { it.language == "other" } ?: languages.firstOrNull())?.names.orEmpty()
        return names.find { it.default && it.name.isNotBlank() }?.name
            ?: names.firstOrNull { it.name.isNotBlank() }?.name ?: "#$id"
    }

    fun originalName(): String? = languages.flatMap { it.names }.lastOrNull { it.original && it.name.isNotBlank() }?.name
}

@Serializable
internal data class TagDto(val tagId: Long, val tagNameList: List<TagLanguageDto> = emptyList()) {
    fun domain(): VideoTag {
        if (tagId <= 0) throw ApiException(ApiFailure.INVALID_RESPONSE)
        return VideoTag(tagId, tagNameList.map { language ->
            TagLanguage(language.lang, language.tagName.map { TagName(it.name, it.isDefault, it.isOriginalTagName) })
        })
    }
}

@Serializable
internal data class TagLanguageDto(val lang: String, val tagName: List<TagNameDto> = emptyList())
@Serializable
internal data class TagNameDto(val name: String, val isDefault: Boolean = false, val isOriginalTagName: Boolean = false)
