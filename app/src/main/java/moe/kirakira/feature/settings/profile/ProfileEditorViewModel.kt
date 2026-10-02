package moe.kirakira.feature.settings.profile

import android.content.Context
import android.net.Uri
import android.os.Parcelable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.text.Normalizer
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.kirakira.R
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.auth.AccountProfile
import moe.kirakira.data.auth.ProfileLabel
import moe.kirakira.data.profile.ProfileRepository

internal data class ProfileEditorState(
    val original: AccountProfile? = null,
    val draft: AccountProfile? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: ApiFailure? = null,
    val message: Int? = null,
    val completionOnly: Boolean = false,
    val avatar: File? = null,
    val cropSource: File? = null,
    val cropOutput: File? = null,
    val cropViewState: Parcelable? = null,
    val preparingImage: Boolean = false,
    val cropReady: Boolean = false,
    val cropBusy: Boolean = false,
    val confirmDiscard: Boolean = false,
    val fieldErrors: Map<String, Int> = emptyMap(),
    val labelInput: String = "",
) {
    val dirty: Boolean get() = draft != original || avatar != null || labelInput.isNotBlank()
    val editable: Boolean get() = !busy && !completionOnly
}

internal class ProfileEditorViewModel(
    private val repository: ProfileRepository,
    context: Context,
) : ViewModel() {
    private val appContext = context.applicationContext
    private var directory = newDirectory()
    private val _state = MutableStateFlow(ProfileEditorState())
    val state = _state.asStateFlow()
    private var task: Job? = null
    private var imageTask: Job? = null
    private var cropStartedOutput: File? = null
    private var identity = repository.session.value.activeUuid
    private var revision = repository.session.value.revision
    val accountRevision: Long get() = revision
    val canNavigateBack: Boolean get() = !_state.value.dirty && !_state.value.busy &&
        !_state.value.preparingImage && _state.value.cropSource == null

    init {
        viewModelScope.launch {
            repository.session.collect { session ->
                if (session.isLoading || session.isBusy) return@collect
                if (session.activeUuid == identity && session.revision == repository.publishedRevision) {
                    revision = session.revision
                    return@collect
                }
                if (session.activeUuid != identity || session.revision != revision) {
                    identity = session.activeUuid
                    revision = session.revision
                    task?.cancel()
                    imageTask?.cancel()
                    cleanup()
                    _state.value = ProfileEditorState()
                    load()
                }
            }
        }
        load()
    }

    fun load() {
        if (identity == null) {
            _state.value = ProfileEditorState(loading = false)
            return
        }
        task?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        task = viewModelScope.launch {
            try {
                val profile = repository.load()
                _state.value = ProfileEditorState(original = profile, draft = profile, loading = false)
            } catch (error: ApiException) {
                _state.update { it.copy(loading = false, error = error.failure) }
            }
        }
    }

    fun edit(field: String, value: String) {
        if (!_state.value.editable) return
        if (field == "labelInput") {
            _state.update { it.copy(labelInput = value) }
            return
        }
        _state.update { state ->
            val draft = state.draft ?: return@update state
            state.copy(draft = when (field) {
                "username" -> draft.copy(username = value)
                "nickname" -> draft.copy(nickname = value)
                "signature" -> draft.copy(signature = value)
                "birthday" -> draft.copy(birthday = value)
                "gender" -> draft.copy(gender = value)
                else -> draft
            }, fieldErrors = state.fieldErrors - field, message = null, error = null)
        }
    }

    fun addLabel(value: String) {
        if (!_state.value.editable) return
        val name = Normalizer.normalize(value.trim(), Normalizer.Form.NFC)
        _state.update { state ->
            val draft = state.draft ?: return@update state
            if (name.isEmpty() || draft.labels.any { it.labelName == name }) return@update state.copy(labelInput = "")
            val used = draft.labels.map { it.id }.toSet()
            val id = generateSequence(0) { it + 1 }.first { it !in used }
            state.copy(draft = draft.copy(labels = draft.labels + ProfileLabel(id, name)), labelInput = "")
        }
    }

    fun removeLabel(index: Int) {
        if (!_state.value.editable) return
        _state.update { state -> state.copy(draft = state.draft?.let {
            it.copy(labels = it.labels.filterIndexed { i, _ -> i != index })
        }) }
    }

    fun save() {
        if (_state.value.busy || _state.value.preparingImage) return
        if (_state.value.labelInput.isNotBlank()) addLabel(_state.value.labelInput)
        val state = _state.value
        if (state.busy || !state.dirty || state.preparingImage) return
        val draft = state.draft ?: return
        val normalized = draft.copy(username = nfc(draft.username.trim()), nickname = nfc(draft.nickname),
            signature = nfc(draft.signature), gender = nfc(draft.gender),
            labels = draft.labels.map { it.copy(labelName = nfc(it.labelName)) })
        val errors = buildMap {
            if (!validProfileName(normalized.username)) put("username", R.string.profile_name_invalid)
            if (normalized.nickname.isNotEmpty() && !validProfileName(normalized.nickname)) {
                put("nickname", R.string.profile_name_invalid)
            }
            if (normalized.signature.length > 200) put("signature", R.string.profile_bio_invalid)
        }
        if (!state.completionOnly && errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors, message = errors.values.first()) }
            return
        }
        _state.update { it.copy(busy = true, error = null, message = null, fieldErrors = emptyMap()) }
        task = viewModelScope.launch {
            try {
                if (!state.completionOnly && normalized.username != state.original?.username &&
                    !repository.usernameAvailable(normalized.username)) {
                    _state.update { it.copy(fieldErrors = mapOf("username" to R.string.auth_username_taken),
                        message = R.string.auth_username_taken) }
                    return@launch
                }
                val updated = repository.save(normalized, state.avatar)
                currentCoroutineContext().ensureActive()
                revision = repository.session.value.revision
                _state.value = ProfileEditorState(original = updated, draft = updated, loading = false,
                    message = R.string.profile_saved)
                cleanup()
            } catch (error: ApiException) {
                _state.update { it.copy(error = error.failure, completionOnly = repository.needsCompletion) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun prepareAvatar(uri: Uri?, expectedRevision: Long?) {
        if (uri == null || expectedRevision != revision || !_state.value.editable || _state.value.preparingImage) return
        val imageDirectory = directory
        _state.update { it.copy(preparingImage = true, message = null) }
        imageTask = viewModelScope.launch {
            var source: File? = null
            try {
                source = withContext(Dispatchers.IO) {
                    imageDirectory.mkdirs()
                    val file = File.createTempFile("source-", ".img", imageDirectory)
                    try {
                        appContext.contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                var total = 0L
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    if (total > 32 * 1024 * 1024) throw ApiException(ApiFailure.REJECTED)
                                    output.write(buffer, 0, count)
                                }
                            }
                        } ?: throw ApiException(ApiFailure.REJECTED)
                        file
                    } catch (error: Exception) {
                        file.delete()
                        throw error
                    }
                }
                cropStartedOutput = null
                _state.update { it.copy(cropSource = source, cropReady = false, cropViewState = null) }
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                source?.let(::deleteFile)
                _state.update { it.copy(message = R.string.profile_image_failed) }
            } finally {
                _state.update { it.copy(preparingImage = false) }
            }
        }
    }

    fun cropLoaded(source: File, success: Boolean) {
        if (_state.value.cropSource != source) return
        if (success) _state.update { it.copy(cropReady = true) }
        else {
            cancelCrop()
            _state.update { it.copy(message = R.string.profile_image_failed) }
        }
    }

    fun rememberCropViewport(source: File, viewport: Parcelable?) {
        if (_state.value.cropSource == source) {
            _state.update { it.copy(cropViewState = viewport, cropReady = false) }
        }
    }

    fun claimCrop(output: File): Boolean {
        if (_state.value.cropOutput != output || cropStartedOutput == output) return false
        cropStartedOutput = output
        return true
    }

    fun beginCrop() {
        if (!_state.value.cropReady || _state.value.cropBusy) return
        _state.update { it.copy(cropBusy = true) }
        val cropDirectory = directory
        imageTask = viewModelScope.launch {
            try {
                val output = withContext(Dispatchers.IO) { File.createTempFile("avatar-", ".jpg", cropDirectory) }
                _state.update { it.copy(cropOutput = output) }
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                _state.update { it.copy(cropBusy = false, message = R.string.profile_image_failed) }
            }
        }
    }

    fun finishCrop(source: File, output: File, success: Boolean) {
        val state = _state.value
        if (state.cropSource != source || state.cropOutput != output) {
            deleteFile(output)
            return
        }
        if (success) {
            state.avatar?.let(::deleteFile)
            deleteFile(source)
            _state.update { it.copy(avatar = output, cropSource = null, cropOutput = null,
                cropBusy = false, cropReady = false) }
        } else {
            deleteFile(output)
            cropStartedOutput = null
            _state.update { it.copy(cropOutput = null, cropBusy = false, message = R.string.profile_image_failed) }
        }
    }

    fun cancelCrop() {
        imageTask?.cancel()
        _state.value.cropSource?.let(::deleteFile)
        _state.value.cropOutput?.let(::deleteFile)
        _state.update { it.copy(cropSource = null, cropOutput = null, cropBusy = false, cropReady = false) }
        cropStartedOutput = null
    }

    /** Shared by toolbar and host back dispatch, before the host starts a page transition. */
    fun requestBack(): Boolean {
        if (_state.value.cropBusy) return false
        if (_state.value.cropSource != null) { cancelCrop(); return false }
        if (_state.value.busy || _state.value.preparingImage) return false
        if (_state.value.dirty) {
            _state.update { it.copy(confirmDiscard = true) }
            return false
        }
        return true
    }

    fun dismissDiscard() { _state.update { it.copy(confirmDiscard = false) } }
    fun dismissMessage() { _state.update { it.copy(message = null) } }
    private fun deleteFile(file: File) { cleanupExecutor.execute { file.delete() } }
    private fun newDirectory() = File(appContext.cacheDir, "profile-editor/${UUID.randomUUID()}")
    private fun cleanup() {
        val oldDirectory = directory
        directory = newDirectory()
        val imageJob = imageTask
        if (imageJob != null && !imageJob.isCompleted) {
            imageJob.invokeOnCompletion { cleanupExecutor.execute { oldDirectory.deleteRecursively() } }
        } else cleanupExecutor.execute { oldDirectory.deleteRecursively() }
    }
    override fun onCleared() { cleanup() }

    private companion object {
        val cleanupExecutor = Executors.newSingleThreadExecutor()
    }
}

private fun nfc(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC)

// Match Rosales ValidTool.validateNameField, including its boundary and separator restrictions.
private val profileNamePattern = Regex("^(?![\\s_-])(?!.*[\\s_-]{2})[a-zA-Z0-9\\-\\uAC00-\\uD7AF\\u3040-\\u30FF\\u4E00-\\u9FAF\\u00C0-\\u1EF9_\\s]+(?<![\\s_-])$")
private fun validProfileName(value: String): Boolean = value.length in 1..20 &&
    value == value.trim() && !value.contains("  ") && profileNamePattern.matches(value)
