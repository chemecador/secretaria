package com.chemecador.secretaria.login

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Durable representation of a Firebase auth session, shared across non-Android targets.
 *
 * Stored by platform-specific [SessionStore] implementations so users don't need to
 * sign in again after the process restarts.
 */
@Serializable
internal data class PersistedAuthSession(
    val userId: String,
    val email: String? = null,
    val idToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
)

/**
 * Platform-agnostic persistence contract for [PersistedAuthSession].
 *
 * - JVM/Desktop: encrypted-at-rest is not guaranteed; we rely on user-home ACLs.
 * - JS browser: backed by `localStorage`, degrades to no-op in private mode or over quota.
 * - iOS: backed by Keychain (`kSecClassGenericPassword`).
 * - Android/Wasm don't use this store (Firebase SDK handles persistence / target is fake).
 */
internal interface SessionStore {
    suspend fun load(): PersistedAuthSession?
    suspend fun save(session: PersistedAuthSession)
    suspend fun clear()
}

/**
 * Default fallback used when a target has not wired a real [SessionStore] yet, or in tests
 * that don't care about persistence. All operations are in-memory and lost on restart.
 */
internal object NoOpSessionStore : SessionStore {
    override suspend fun load(): PersistedAuthSession? = null
    override suspend fun save(session: PersistedAuthSession) = Unit
    override suspend fun clear() = Unit
}

internal val persistedAuthSessionJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun PersistedAuthSession.encodeToJsonString(): String =
    persistedAuthSessionJson.encodeToString(PersistedAuthSession.serializer(), this)

internal fun decodePersistedAuthSession(json: String): PersistedAuthSession? =
    try {
        persistedAuthSessionJson.decodeFromString(PersistedAuthSession.serializer(), json)
    } catch (_: Throwable) {
        null
    }
