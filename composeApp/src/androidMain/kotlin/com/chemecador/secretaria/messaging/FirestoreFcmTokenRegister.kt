package com.chemecador.secretaria.messaging

import com.chemecador.secretaria.login.AuthRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.TimeZone
import java.util.Locale

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
                        // El vencimiento de un recordatorio es una fecha flotante: el servidor
                        // necesita la zona del dispositivo para saber cuando enviar el aviso.
                        "timeZoneId" to TimeZone.currentSystemDefault().id,
                        // El servidor compone el texto del aviso, asi que necesita saber en
                        // que idioma esta el dispositivo. Un idioma sin traduccion cae a ingles.
                        "language" to Locale.getDefault().language,
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
