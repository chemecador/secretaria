package com.chemecador.secretaria.messaging

import com.chemecador.secretaria.login.AuthRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class FirestoreFcmTokenRegister(
    private val authRepository: AuthRepository,
) : FcmTokenRegister {

    private val firestore get() = FirebaseFirestore.getInstance()
    private val messaging get() = FirebaseMessaging.getInstance()

    override suspend fun registerCurrentToken(): Result<Unit> {
        return try {
            val userId = authRepository.currentUserId ?: return Result.success(Unit)
            val token = messaging.token.await()
            if (token.isNullOrBlank()) return Result.success(Unit)
            firestore.collection(USERS).document(userId)
                .collection(FCM_TOKENS).document(token)
                .set(
                    mapOf(
                        "token" to token,
                        "platform" to "android",
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unregisterCurrentToken(): Result<Unit> {
        return try {
            val userId = authRepository.currentUserId ?: return Result.success(Unit)
            val token = try {
                messaging.token.await()
            } catch (_: Exception) {
                null
            }
            if (token.isNullOrBlank()) return Result.success(Unit)
            firestore.collection(USERS).document(userId)
                .collection(FCM_TOKENS).document(token)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val USERS = "users"
        const val FCM_TOKENS = "fcm_tokens"
    }
}
