package moe.kirakira.data.kaomoji

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray

/** App-local preferences, deliberately outside the settings backup allowlist. */
internal class RecentKaomojiStore private constructor(context: Context) {
    private val state = MutableStateFlow<List<String>>(emptyList())
    val recent = state.asStateFlow()
    private val selections = Channel<String>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            val preferences = context.getSharedPreferences("kirakira_kaomoji", Context.MODE_PRIVATE)
            val catalog = kaomojiCatalog.values.flatten().toSet()
            state.value = try {
                val saved = JSONArray(preferences.getString("recent", "[]"))
                List(saved.length()) { saved.optString(it) }.filter { it in catalog }.distinct().take(24)
            } catch (_: org.json.JSONException) {
                emptyList()
            } catch (_: ClassCastException) {
                emptyList()
            }
            // One consumer serializes the initial read and every write, even across video pages.
            for (text in selections) {
                if (text !in catalog) continue
                val next = (listOf(text) + state.value).distinct().take(24)
                state.value = next
                preferences.edit().putString("recent", JSONArray(next).toString()).commit()
            }
        }
    }

    fun record(text: String) {
        selections.trySend(text)
    }

    companion object {
        @Volatile private var instance: RecentKaomojiStore? = null

        fun get(context: Context): RecentKaomojiStore = instance ?: synchronized(this) {
            instance ?: RecentKaomojiStore(context.applicationContext).also { instance = it }
        }
    }
}
