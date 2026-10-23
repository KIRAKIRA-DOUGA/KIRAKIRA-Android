package moe.kirakira.data.search

import android.annotation.SuppressLint
import android.content.Context
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import moe.kirakira.data.content.VideoTag

@Serializable
internal data class SearchHistoryEntry(
    val keyword: String? = null,
    val tags: List<VideoTag> = emptyList(),
) {
    val key: String get() = keyword?.let { "keyword:$it" }
        ?: "tags:${tags.map { it.id }.distinct().sorted().joinToString(",")}"
}

@Serializable
private data class StoredSearchHistory(val version: Int = 1, val entries: List<SearchHistoryEntry> = emptyList())

/** Local search terms are deliberately outside the system backup allowlist. */
internal class SearchHistoryRepository(context: Context, private val baseUrl: String) {
    private val applicationContext = context.applicationContext
    private val preferences by lazy {
        applicationContext.getSharedPreferences("kirakira_search_history", Context.MODE_PRIVATE)
    }
    private val mutex = Mutex()
    private val committed = mutableMapOf<String, List<SearchHistoryEntry>>()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(accountUuid: String?): List<SearchHistoryEntry>? = access(accountUuid)

    suspend fun record(accountUuid: String?, entry: SearchHistoryEntry): List<SearchHistoryEntry>? =
        access(accountUuid) { current -> normalize(listOf(entry) + current) }

    suspend fun remove(accountUuid: String?, key: String): List<SearchHistoryEntry>? =
        access(accountUuid) { entries -> entries.filterNot { it.key == key } }

    suspend fun clear(accountUuid: String?): List<SearchHistoryEntry>? = access(accountUuid) { emptyList() }

    private suspend fun access(
        accountUuid: String?,
        change: ((List<SearchHistoryEntry>) -> List<SearchHistoryEntry>)? = null,
    ): List<SearchHistoryEntry>? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val owner = MessageDigest.getInstance("SHA-256")
                .digest("$baseUrl\n${accountUuid ?: "guest"}".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            try {
                val current = committed[owner] ?: readPartition(owner)
                if (change == null) return@withContext current
                val next = change(current)
                // Publish only confirmed writes; SharedPreferences itself changes its memory before commit returns.
                @SuppressLint("ApplySharedPref", "UseKtx")
                val saved = preferences.edit().putString(owner, json.encodeToString(StoredSearchHistory(entries = next)))
                    .commit()
                if (!saved) return@withContext null
                committed[owner] = next
                next
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }
    }

    /** Reads and caches one partition; unreadable data is discarded so it cannot block history forever. */
    private fun readPartition(owner: String): List<SearchHistoryEntry> {
        val raw = try {
            preferences.getString(owner, null)
        } catch (_: ClassCastException) {
            discardPartition(owner)
            return emptyList<SearchHistoryEntry>().also { committed[owner] = it }
        }
        val decoded = if (raw == null) {
            emptyList()
        } else {
            try {
                val stored = json.decodeFromString<StoredSearchHistory>(raw)
                if (stored.version == 1) normalize(stored.entries) else null
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        val entries = if (decoded == null) {
            discardPartition(owner)
            emptyList()
        } else {
            decoded
        }
        committed[owner] = entries
        return entries
    }

    private fun discardPartition(owner: String) {
        @SuppressLint("ApplySharedPref", "UseKtx")
        preferences.edit().remove(owner).commit()
    }

    private fun normalize(entries: List<SearchHistoryEntry>): List<SearchHistoryEntry> = entries.mapNotNull { entry ->
        if (entry.keyword != null) {
            entry.keyword.trim().takeIf { it.isNotEmpty() }?.let { SearchHistoryEntry(keyword = it) }
        } else {
            entry.tags.filter { it.id > 0 }.distinctBy { it.id }.takeIf { it.isNotEmpty() }
                ?.let { SearchHistoryEntry(tags = it) }
        }
    }.distinctBy { it.key }.take(30)
}
