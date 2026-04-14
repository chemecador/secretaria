package com.chemecador.secretaria.login

import platform.Foundation.NSUserDefaults

/**
 * iOS [SessionStore] for v1.
 *
 * Backed by [NSUserDefaults] for now. The approved plan noted Keychain
 * (`kSecClassGenericPassword`) as the "right" choice for storing tokens, but the
 * Kotlin/Native Security interop is verbose and was deferred to keep this change small.
 *
 * TODO(secretaria): migrate to Keychain via `platform.Security.SecItem*`. Firebase
 *  `refreshToken` values are long-lived, so Keychain storage is the proper long-term home.
 */
internal class IosSessionStore(
    private val key: String = DEFAULT_KEY,
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : SessionStore {

    override suspend fun load(): PersistedAuthSession? {
        val raw = defaults.stringForKey(key) ?: return null
        return decodePersistedAuthSession(raw)
    }

    override suspend fun save(session: PersistedAuthSession) {
        val json = session.encodeToJsonString()
        defaults.setObject(json, forKey = key)
    }

    override suspend fun clear() {
        defaults.removeObjectForKey(key)
    }

    private companion object {
        const val DEFAULT_KEY = "com.chemecador.secretaria.auth.session"
    }
}
