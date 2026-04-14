package com.chemecador.secretaria.login

/**
 * In-memory [SessionStore] for tests. Mirrors the production contract without touching
 * the filesystem / localStorage / NSUserDefaults, and exposes the held value for
 * assertions.
 */
internal class FakeSessionStore(
    initial: PersistedAuthSession? = null,
) : SessionStore {

    var stored: PersistedAuthSession? = initial
        private set

    var saveCount: Int = 0
        private set

    var clearCount: Int = 0
        private set

    override suspend fun load(): PersistedAuthSession? = stored

    override suspend fun save(session: PersistedAuthSession) {
        stored = session
        saveCount += 1
    }

    override suspend fun clear() {
        stored = null
        clearCount += 1
    }
}
