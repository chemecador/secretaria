package com.chemecador.secretaria.login

import kotlinx.browser.window

/**
 * JS browser [SessionStore] backed by `window.localStorage`.
 *
 * Degrades to a no-op when the browser rejects access (private mode, quota exhausted,
 * disabled storage) — login still works, just without auto-login persistence.
 */
internal class LocalStorageSessionStore(
    private val key: String = DEFAULT_KEY,
) : SessionStore {

    override suspend fun load(): PersistedAuthSession? {
        val raw = try {
            window.localStorage.getItem(key)
        } catch (_: Throwable) {
            return null
        } ?: return null
        return decodePersistedAuthSession(raw)
    }

    override suspend fun save(session: PersistedAuthSession) {
        val json = session.encodeToJsonString()
        try {
            window.localStorage.setItem(key, json)
        } catch (_: Throwable) {
            // ignore — private mode / quota. Memory cache still holds the session.
        }
    }

    override suspend fun clear() {
        try {
            window.localStorage.removeItem(key)
        } catch (_: Throwable) {
            // ignore
        }
    }

    private companion object {
        const val DEFAULT_KEY = "secretaria.auth.session"
    }
}
