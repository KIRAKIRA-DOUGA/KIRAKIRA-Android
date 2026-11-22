package moe.kirakira.feature.settings.management

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.kirakira.R
import moe.kirakira.core.network.ApiException
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.data.content.PublicProfile
import moe.kirakira.data.content.VideoTag
import moe.kirakira.data.settings.AccountSettingsRepository
import moe.kirakira.data.settings.RuleCategory
import moe.kirakira.data.settings.RuleEntry
import moe.kirakira.data.settings.RulePage
import moe.kirakira.data.settings.UnsafeRuleException
import moe.kirakira.ui.components.status.messageRes
import moe.kirakira.ui.content.TagSearchLookup
import moe.kirakira.ui.content.TagSearchState

internal data class RuleEditor(
    val accountRevision: Long? = null,
    val input: String = "",
    val profile: PublicProfile? = null,
    val tagSearch: TagSearchState = TagSearchState(),
    val selectedTag: VideoTag? = null,
    val error: Int? = null,
)

internal class RuleManagementViewModel(
    val category: RuleCategory,
    repository: AccountSettingsRepository,
    private val content: ContentRepository,
) : SettingsViewModel(repository) {
    private val _rules = MutableStateFlow(SettingsLoad<RulePage>())
    val rules = _rules.asStateFlow()
    private val _editor = MutableStateFlow(RuleEditor())
    val editor = _editor.asStateFlow()
    private var read: Job? = null
    private val tagSearch = TagSearchLookup(
        launchAccountTask = ::task,
        isCurrentAccount = { it == session.value.revision },
        fetch = content::searchTags,
        publish = { _editor.value = _editor.value.copy(tagSearch = it) },
    )
    private val canEdit: Boolean
        get() = ready && revision == session.value.revision && _editor.value.accountRevision == revision &&
            !busyState.value

    init {
        observe({
            tagSearch.cancel()
            _rules.value = SettingsLoad()
            _editor.value = RuleEditor()
        }, ::refresh)
    }

    fun refresh() = loadPage(1)
    fun more() = loadPage((_rules.value.data?.page ?: 0) + 1)

    private fun loadPage(page: Int) {
        if (!ready || busyState.value || _rules.value.loading) return
        read?.cancel()
        _rules.value = _rules.value.copy(loading = true, error = null, appending = page > 1)
        read = task { expected ->
            try {
                val result = repository.rules(category, page, expected)
                val previous = if (page == 1) emptyList() else _rules.value.data?.entries.orEmpty()
                _rules.value = SettingsLoad(result.copy(entries = (previous + result.entries).distinctBy { it.value }))
            } catch (error: ApiException) {
                currentCoroutineContext().ensureActive()
                if (expected == session.value.revision) _rules.value = _rules.value.readFailed(error.failure)
            }
        }
    }

    fun openEditor() {
        if (ready && revision == session.value.revision && !busyState.value && _editor.value.accountRevision == null) {
            _editor.value = RuleEditor(accountRevision = revision)
        }
    }

    fun closeEditor() {
        if (busyState.value) return
        tagSearch.cancel()
        _editor.value = RuleEditor()
    }

    fun input(value: String) {
        if (!canEdit) return
        if (category == RuleCategory.TAG) {
            tagSearch.search(value, _editor.value.tagSearch, debounce = true)
        } else {
            _editor.value = RuleEditor(accountRevision = revision, input = value)
        }
    }

    fun retryTagSearch() {
        if (!canEdit || category != RuleCategory.TAG) return
        val state = _editor.value.tagSearch
        tagSearch.search(state.query, state, debounce = false)
    }

    fun dismissEditorError() {
        _editor.value = _editor.value.copy(error = null)
    }

    fun selectTag(tag: VideoTag) {
        if (!canEdit || category != RuleCategory.TAG) return
        val editor = _editor.value
        _editor.value = editor.copy(selectedTag = tag.takeUnless { editor.selectedTag?.id == it.id })
    }

    fun submit() {
        if (!canEdit) return
        val value = _editor.value.input.trim()
        when (category) {
            RuleCategory.BLOCK, RuleCategory.HIDE -> {
                val uid = value.toLongOrNull()
                if (uid == null || uid <= 0 || !value.all(Char::isDigit)) {
                    _editor.value = _editor.value.copy(error = R.string.management_invalid_uid)
                    return
                }
                if (uid == session.value.activeProfile?.uid) {
                    _editor.value = _editor.value.copy(error = R.string.management_self_rule)
                    return
                }
                val profile = _editor.value.profile
                if (profile != null) change(profile.uid.toString(), false)
                else editorTask { expected ->
                    val details = content.profileDetails(uid, expected)
                    _editor.value = _editor.value.copy(profile = details.profile ?: PublicProfile(uid, uid.toString()))
                }
            }
            RuleCategory.TAG -> _editor.value.selectedTag?.let { change(it.id.toString(), false) }
            RuleCategory.KEYWORD, RuleCategory.REGEX -> {
                if (value.isBlank() || value.length > 30) {
                    _editor.value = _editor.value.copy(error = R.string.management_invalid_rule)
                    return
                }
                change(value, false)
            }
        }
    }

    private fun editorTask(block: suspend (Long) -> Unit) {
        busyState.value = true
        _editor.value = _editor.value.copy(error = null)
        task { expected ->
            try { block(expected) }
            catch (error: ApiException) { _editor.value = _editor.value.copy(error = error.failure.messageRes()) }
            finally { if (expected == revision) busyState.value = false }
        }
    }

    fun remove(entry: RuleEntry) {
        val value = when (category) {
            RuleCategory.BLOCK, RuleCategory.HIDE -> entry.uid?.toString() ?: return
            else -> entry.value
        }
        change(value, true)
    }

    private fun change(value: String, remove: Boolean) {
        if (!ready || revision != session.value.revision || busyState.value) return
        read?.cancel()
        _rules.value = _rules.value.copy(loading = false)
        busyState.value = true
        _editor.value = _editor.value.copy(error = null)
        task { expected ->
            var changed = false
            var uncertain = false
            try {
                repository.changeRule(category, value, remove, expected)
                if (expected != session.value.revision) return@task
                changed = true
                if (remove) {
                    _rules.value.data?.let { page ->
                        _rules.value = SettingsLoad(page.copy(
                            entries = page.entries.filterNot { it.value == value || it.uid?.toString() == value },
                            total = (page.total - 1).coerceAtLeast(0),
                        ))
                    }
                }
                tagSearch.cancel()
                _editor.value = RuleEditor()
                messageState.value = if (remove) R.string.management_removed else R.string.management_added
            } catch (_: UnsafeRuleException) {
                if (expected == session.value.revision) {
                    _editor.value = _editor.value.copy(error = R.string.management_unsafe_regex)
                }
            } catch (error: ApiException) {
                currentCoroutineContext().ensureActive()
                if (expected != session.value.revision) return@task
                uncertain = error.failure.isUncertainMutation
                val message = if (uncertain) R.string.management_change_unconfirmed else error.failure.messageRes()
                if (remove) messageState.value = message else _editor.value = _editor.value.copy(error = message)
            } finally {
                if (expected == revision) {
                    busyState.value = false
                    if (changed || uncertain) refresh()
                }
            }
        }
    }
}
