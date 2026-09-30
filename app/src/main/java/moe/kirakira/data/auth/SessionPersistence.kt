package moe.kirakira.data.auth

/** Production uses the encrypted vault; local tests use isolated in-memory storage. */
internal interface SessionPersistence {
    suspend fun read(): StoredSessions
    suspend fun write(state: StoredSessions)
    suspend fun reset()
}
