package moe.kirakira.data.kaomoji

import android.content.Context
import androidx.core.content.edit
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
    private val mutex = Mutex()
    private val catalog = kaomojiCatalog.values.flatten().toSet()
    private val preferences by lazy {
        applicationContext.getSharedPreferences("kirakira_kaomoji", Context.MODE_PRIVATE)
    }
    private var loaded = false

    suspend fun load() {
        mutex.withLock {
            withContext(Dispatchers.IO) { loadLocked() }
        }
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
            return false
        }
        state.value = entries
        loaded = true
        return true
    }

    /** Serializes first read and writes; a later selection retries the complete in-memory list. */
    suspend fun record(text: String) = mutex.withLock {
        // Take the lock before dispatching so rapid UI selections retain their order.
        withContext(Dispatchers.IO) {
            if (text !in catalog || !loadLocked()) return@withContext
            val next = (listOf(text) + state.value).distinct().take(24)
            state.value = next
            try {
                preferences.edit(commit = true) {
                    putString("recent", JSONArray(next).toString())
                }
            } catch (_: SecurityException) {
                // Keep the in-memory list so the next selection can retry the whole write.
            }
        }
    }
}
