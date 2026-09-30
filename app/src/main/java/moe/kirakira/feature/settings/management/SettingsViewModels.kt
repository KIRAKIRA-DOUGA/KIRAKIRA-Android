package moe.kirakira.feature.settings.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.kirakira.R
import moe.kirakira.core.network.ApiException
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.data.content.PublicProfile
import moe.kirakira.data.settings.AccountSettingsRepository
import moe.kirakira.data.settings.Invitation
import moe.kirakira.data.settings.InvitationCreation
import moe.kirakira.data.settings.RuleCategory
import moe.kirakira.data.settings.RuleEntry
import moe.kirakira.data.settings.RulePage
import moe.kirakira.data.settings.RuleTag
import moe.kirakira.data.settings.UnsafeRuleException
import moe.kirakira.ui.components.messageRes

internal data class SettingsLoad<T>(
    val data: T? = null,
    val loading: Boolean = false,
    val error: Int? = null,
    val appending: Boolean = false,
)

/** Jobs and drafts are owned by the currently published session revision. */
internal abstract class SettingsViewModel(protected val repository: AccountSettingsRepository) : ViewModel() {
    val session = repository.session
    protected val jobs = mutableSetOf<Job>()
    protected var revision = session.value.revision
    protected val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    protected val _message = MutableStateFlow<Int?>(null)
    val message = _message.asStateFlow()
    protected val ready: Boolean
        get() = !session.value.isLoading && !session.value.isBusy && session.value.activeProfile != null

    protected fun observe(reset: () -> Unit, refresh: () -> Unit) {
        viewModelScope.launch {
            session.map { Triple(it.revision, it.isLoading, it.isBusy) }.distinctUntilChanged().collect {
                revision = session.value.revision
                jobs.toList().forEach { it.cancel() }
                jobs.clear()
                _busy.value = false
                _message.value = null
                reset()
                if (ready) refresh()
            }
        }
    }

    protected fun task(block: suspend (Long) -> Unit): Job {
        val expected = revision
        val job = viewModelScope.launch { block(expected) }
        jobs += job
        job.invokeOnCompletion { jobs.remove(job) }
        return job
    }

    fun dismissMessage() { _message.value = null }
    protected fun report(error: ApiException) { _message.value = error.failure.messageRes() }
}

internal class BlockingOverviewViewModel(repository: AccountSettingsRepository) : SettingsViewModel(repository) {
    private val _counts = MutableStateFlow(SettingsLoad<Map<RuleCategory, Int>>())
    val counts = _counts.asStateFlow()
    private var read: Job? = null

    init {
        observe({ _counts.value = SettingsLoad() }, ::refresh)
        viewModelScope.launch { repository.ruleVersion.drop(1).collect { refresh() } }
    }

    fun refresh() {
        if (!ready) return
        read?.cancel()
        _counts.value = _counts.value.copy(loading = true, error = null)
        read = task { expected ->
            try {
                val counts = coroutineScope {
                    RuleCategory.entries.map { category ->
                        async { category to repository.rules(category, 1, expected, 1).total }
                    }.awaitAll().toMap()
                }
                _counts.value = SettingsLoad(counts)
            } catch (error: ApiException) {
                _counts.value = _counts.value.copy(loading = false, error = error.failure.messageRes())
            }
        }
    }
}

internal data class RuleEditor(
    val open: Boolean = false,
    val input: String = "",
    val profile: PublicProfile? = null,
    val tags: List<RuleTag>? = null,
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

    init { observe({ _rules.value = SettingsLoad(); _editor.value = RuleEditor() }, ::refresh) }

    fun refresh() = loadPage(1)
    fun more() = loadPage((_rules.value.data?.page ?: 0) + 1)

    private fun loadPage(page: Int) {
        if (!ready || _busy.value || _rules.value.loading) return
        read?.cancel()
        _rules.value = _rules.value.copy(loading = true, error = null, appending = page > 1)
        read = task { expected ->
            try {
                val result = repository.rules(category, page, expected)
                val previous = if (page == 1) emptyList() else _rules.value.data?.entries.orEmpty()
                _rules.value = SettingsLoad(result.copy(entries = (previous + result.entries).distinctBy { it.value }))
            } catch (error: ApiException) {
                _rules.value = _rules.value.copy(loading = false, error = error.failure.messageRes())
            }
        }
    }

    fun openEditor() { if (ready && !_busy.value) _editor.value = RuleEditor(open = true) }
    fun closeEditor() { if (!_busy.value) _editor.value = RuleEditor() }
    fun input(value: String) {
        if (!_busy.value) _editor.value = RuleEditor(open = true, input = value)
    }

    fun submit() {
        if (!ready || _busy.value) return
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
                else editorTask { expected -> _editor.value = _editor.value.copy(profile = content.profile(uid, expected)) }
            }
            RuleCategory.TAG -> {
                if (value.isBlank()) return
                editorTask { expected -> _editor.value = _editor.value.copy(tags = repository.searchTags(value, expected)) }
            }
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
        _busy.value = true
        _editor.value = _editor.value.copy(error = null)
        task { expected ->
            try { block(expected) }
            catch (error: ApiException) { _editor.value = _editor.value.copy(error = error.failure.messageRes()) }
            finally { if (expected == revision) _busy.value = false }
        }
    }

    fun selectTag(tag: RuleTag) = change(tag.id.toString(), false)
    fun remove(entry: RuleEntry) {
        val value = when (category) {
            RuleCategory.BLOCK, RuleCategory.HIDE -> entry.uid?.toString() ?: return
            else -> entry.value
        }
        change(value, true)
    }

    private fun change(value: String, remove: Boolean) {
        if (!ready || _busy.value) return
        read?.cancel()
        _rules.value = _rules.value.copy(loading = false)
        _busy.value = true
        _editor.value = _editor.value.copy(error = null)
        task { expected ->
            var changed = false
            try {
                repository.changeRule(category, value, remove, expected)
                changed = true
                if (remove) {
                    _rules.value.data?.let { page ->
                        _rules.value = SettingsLoad(page.copy(
                            entries = page.entries.filterNot { it.value == value || it.uid?.toString() == value },
                            total = (page.total - 1).coerceAtLeast(0),
                        ))
                    }
                }
                _editor.value = RuleEditor()
                _message.value = if (remove) R.string.management_removed else R.string.management_added
            } catch (_: UnsafeRuleException) {
                _editor.value = _editor.value.copy(error = R.string.management_unsafe_regex)
            } catch (error: ApiException) {
                if (remove) report(error) else _editor.value = _editor.value.copy(error = error.failure.messageRes())
            } finally {
                if (expected == revision) {
                    _busy.value = false
                    if (changed) refresh()
                }
            }
        }
    }
}

internal class InvitationsViewModel(repository: AccountSettingsRepository) : SettingsViewModel(repository) {
    private val _invitations = MutableStateFlow(SettingsLoad<List<Invitation>>())
    val invitations = _invitations.asStateFlow()
    private var read: Job? = null
    // Preserve a confirmed generation if the following read fails or is briefly stale.
    private val generated = mutableMapOf<String, Invitation>()

    init { observe({ generated.clear(); _invitations.value = SettingsLoad() }, ::refresh) }

    fun refresh() {
        if (!ready || _busy.value) return
        read?.cancel()
        _invitations.value = _invitations.value.copy(loading = true, error = null)
        read = task { expected ->
            try {
                val result = repository.invitations(expected)
                result.forEach { generated.remove(it.code) }
                _invitations.value = SettingsLoad((result + generated.values).distinctBy { it.code })
            } catch (error: ApiException) {
                _invitations.value = _invitations.value.copy(loading = false, error = error.failure.messageRes())
            }
        }
    }

    fun create() {
        if (!ready || _busy.value) return
        read?.cancel()
        _invitations.value = _invitations.value.copy(loading = false)
        _busy.value = true
        task { expected ->
            var created = false
            try {
                when (val result = repository.createInvitation(expected)) {
                    InvitationCreation.CoolingDown -> _message.value = R.string.invitation_cooling_down
                    is InvitationCreation.Created -> {
                        generated[result.invitation.code] = result.invitation
                        _invitations.value = SettingsLoad(
                            (_invitations.value.data.orEmpty() + result.invitation).distinctBy { it.code },
                        )
                        _message.value = R.string.invitation_created
                        created = true
                    }
                }
            } catch (error: ApiException) { report(error) }
            finally {
                if (expected == revision) {
                    _busy.value = false
                    if (created) refresh()
                }
            }
        }
    }
}
