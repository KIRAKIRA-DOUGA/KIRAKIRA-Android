package moe.kirakira.data.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import moe.kirakira.core.image.publicMediaUrl
import moe.kirakira.core.network.ApiClient
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.auth.AuthRepository

/** Account-scoped settings. Only the server applies these rules to content. */
internal class AccountSettingsRepository(private val api: ApiClient, private val auth: AuthRepository) {
    val session = auth.session
    private val _ruleVersion = MutableStateFlow(0L)
    val ruleVersion = _ruleVersion.asStateFlow()

    private suspend fun <T> request(revision: Long, block: suspend (String) -> T): T {
        val snapshot = auth.requestSession()
        if (snapshot.revision != revision) throw CancellationException("Account changed")
        val account = snapshot.account ?: throw ApiException(ApiFailure.SESSION_EXPIRED)
        val result = try {
            block(account.cookie())
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (!auth.isCurrent(snapshot)) throw CancellationException("Account changed")
            if (error is ApiException && error.failure == ApiFailure.SESSION_EXPIRED) auth.expireRequestSession(snapshot)
            throw error
        }
        currentCoroutineContext().ensureActive()
        if (!auth.isCurrent(snapshot)) throw CancellationException("Account changed")
        return result
    }

    suspend fun rules(category: RuleCategory, page: Int, revision: Long, pageSize: Int = 20): RulePage =
        request(revision) { cookie ->
            val result = api.get<RulesDto>("block/list", mapOf(
                "type" to category.wireName, "page" to page.toString(), "pageSize" to pageSize.toString(),
            ), cookie)
            checkSuccess(result.success)
            val entries = result.result ?: invalidResponse()
            // Rosales omits the aggregation count when the collection has no matching rows.
            val total = result.blocklistCount?.takeIf { it >= 0 }
                ?: if (result.blocklistCount == null && entries.isEmpty()) 0 else invalidResponse()
            RulePage(entries.map { entry ->
                if (entry.type != category.wireName || entry.value.isBlank()) invalidResponse()
                RuleEntry(entry.value, entry.createDateTime, entry.uid,
                    entry.userNickname?.takeIf(String::isNotBlank) ?: entry.username,
                    entry.avatar?.let(::publicMediaUrl), entry.tag?.domain())
            }.distinctBy { it.value }, total, page)
        }

    suspend fun changeRule(category: RuleCategory, value: String, remove: Boolean, revision: Long) {
        request(revision) { cookie ->
            val body = buildJsonObject {
                when (category) {
                    RuleCategory.BLOCK, RuleCategory.HIDE, RuleCategory.TAG ->
                        put(category.field, value.toLongOrNull()?.takeIf { it > 0 } ?: throw ApiException(ApiFailure.REJECTED))
                    else -> put(category.field, value)
                }
            }.toString()
            val path = "block/${if (remove) "delete/" else ""}${category.endpoint}"
            val result = if (remove) api.delete<MutationDto>(path, body, cookie)
                else api.post<MutationDto>(path, body, cookie)
            if (result.unsafeRegex) throw UnsafeRuleException()
            checkSuccess(result.success)
        }
        _ruleVersion.update { it + 1 }
    }

    suspend fun searchTags(query: String, revision: Long): List<RuleTag> = request(revision) { cookie ->
        val result = api.get<TagsDto>("video/tag/search", mapOf("tagName" to query), cookie)
        checkSuccess(result.success)
        (result.result ?: invalidResponse()).map { it.domain() }.distinctBy { it.id }
    }

    suspend fun invitations(revision: Long): List<Invitation> = request(revision) { cookie ->
        val result = api.get<InvitationsDto>("user/myInvitationCode", cookie = cookie)
        checkSuccess(result.success)
        (result.invitationCodeResult ?: invalidResponse()).map { it.domain(session.value.activeProfile?.uid) }.distinctBy { it.code }
    }

    suspend fun createInvitation(revision: Long): InvitationCreation = request(revision) { cookie ->
        val result = api.post<InvitationCreationDto>("user/createInvitationCode", "{}", cookie)
        checkSuccess(result.success)
        if (result.isCoolingDown) InvitationCreation.CoolingDown
        else InvitationCreation.Created((result.invitationCodeResult ?: invalidResponse()).domain(session.value.activeProfile?.uid))
    }
}

private fun checkSuccess(success: Boolean) { if (!success) throw ApiException(ApiFailure.REJECTED) }
private fun invalidResponse(): Nothing = throw ApiException(ApiFailure.INVALID_RESPONSE)

@Serializable
private data class RulesDto(val success: Boolean, val blocklistCount: Int? = null, val result: List<RuleDto>? = null)
@Serializable
private data class RuleDto(
    val type: String,
    val value: String,
    val createDateTime: Long,
    val uid: Long? = null,
    val username: String? = null,
    val userNickname: String? = null,
    val avatar: String? = null,
    val tag: TagDto? = null,
)
@Serializable
private data class MutationDto(val success: Boolean, val unsafeRegex: Boolean = false)
@Serializable
private data class TagsDto(val success: Boolean, val result: List<TagDto>? = null)
@Serializable
private data class TagDto(val tagId: Long, val tagNameList: List<TagLanguageDto> = emptyList()) {
    fun domain(): RuleTag {
        if (tagId <= 0) invalidResponse()
        return RuleTag(tagId, tagNameList.map { language ->
            TagLanguage(language.lang, language.tagName.map { TagName(it.name, it.isDefault, it.isOriginalTagName) })
        })
    }
}
@Serializable
private data class TagLanguageDto(val lang: String, val tagName: List<TagNameDto> = emptyList())
@Serializable
private data class TagNameDto(val name: String, val isDefault: Boolean = false, val isOriginalTagName: Boolean = false)
@Serializable
private data class InvitationsDto(val success: Boolean, val invitationCodeResult: List<InvitationDto>? = null)
@Serializable
private data class InvitationCreationDto(
    val success: Boolean,
    val isCoolingDown: Boolean,
    val invitationCodeResult: InvitationDto? = null,
)
@Serializable
private data class InvitationDto(
    val creatorUid: Long,
    val invitationCode: String,
    val generationDateTime: Long,
    val assignee: Long? = null,
) {
    fun domain(expectedUid: Long?): Invitation {
        if (invitationCode.isBlank() || creatorUid != expectedUid) invalidResponse()
        return Invitation(invitationCode, generationDateTime, assignee != null)
    }
}
