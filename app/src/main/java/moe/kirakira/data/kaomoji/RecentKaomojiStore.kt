package moe.kirakira.data.kaomoji

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException

/** App-local preferences, deliberately outside the settings backup allowlist. */
internal class RecentKaomojiStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val state = MutableStateFlow<List<String>>(emptyList())
    val recent = state.asStateFlow()
    private val _persistenceFailed = MutableStateFlow(false)
    val persistenceFailed = _persistenceFailed.asStateFlow()
    private val mutex = Mutex()
    private val catalog = kaomojiCatalog.values.flatten().toSet()
    private val preferences by lazy {
        applicationContext.getSharedPreferences("kirakira_kaomoji", Context.MODE_PRIVATE)
    }
    private var loaded = false

    suspend fun load(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) { loadLocked() }
    }

    private fun loadLocked(): Boolean {
        if (loaded) return true
        val entries = try {
            val saved = JSONArray(preferences.getString("recent", "[]"))
            List(saved.length()) { saved.optString(it) }.filter { it in catalog }.distinct().take(24)
        } catch (_: JSONException) {
            emptyList()
        } catch (_: ClassCastException) {
            emptyList()
        } catch (_: SecurityException) {
            _persistenceFailed.value = true
            return false
        }
        state.value = entries
        loaded = true
        _persistenceFailed.value = false
        return true
    }

    /** Serializes first read and writes; a later selection retries the complete in-memory list. */
    suspend fun record(text: String): Boolean = mutex.withLock {
        // Take the lock before dispatching so rapid UI selections retain their order.
        withContext(Dispatchers.IO) {
            if (text !in catalog) return@withContext true
            if (!loadLocked()) return@withContext false
            val next = (listOf(text) + state.value).distinct().take(24)
            state.value = next
            // Runs on IO; keep commit's result for failure feedback and retry. KTX edit returns no result.
            @SuppressLint("ApplySharedPref", "UseKtx")
            val saved = try {
                preferences.edit().putString("recent", JSONArray(next).toString()).commit()
            } catch (_: SecurityException) {
                false
            }
            _persistenceFailed.value = !saved
            saved
        }
    }
}
