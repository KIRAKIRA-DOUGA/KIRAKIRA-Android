package moe.kirakira.data.auth

/** Production uses private account files; local tests use isolated in-memory storage. */
internal interface SessionPersistence {
    val migrationNotice: SessionNotice? get() = null
    val requiresReload: Boolean get() = false

    suspend fun read(): StoredSessions
    suspend fun write(state: StoredSessions)
    suspend fun reset()
}
