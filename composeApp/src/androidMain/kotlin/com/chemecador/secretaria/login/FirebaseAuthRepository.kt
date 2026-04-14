package com.chemecador.secretaria.login

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository : AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override val currentUserId: String?
        get() = auth.currentUser?.uid

    override val currentUserEmail: String?
        get() = auth.currentUser?.email?.takeUnless { it.isBlank() }

    override suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (_: FirebaseAuthInvalidUserException) {
            Result.failure(AuthException(AuthError.INVALID_USER))
        } catch (_: FirebaseAuthInvalidCredentialsException) {
            Result.failure(AuthException(AuthError.WRONG_PASSWORD))
        } catch (_: Exception) {
            Result.failure(AuthException(AuthError.UNKNOWN))
        }
    }

    override suspend fun signup(email: String, password: String): Result<Unit> {
        return try {
            auth.createUserWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (_: FirebaseAuthUserCollisionException) {
            Result.failure(AuthException(AuthError.USER_ALREADY_EXISTS))
        } catch (_: FirebaseAuthWeakPasswordException) {
            Result.failure(AuthException(AuthError.WEAK_PASSWORD))
        } catch (_: FirebaseAuthInvalidCredentialsException) {
            Result.failure(AuthException(AuthError.INVALID_EMAIL))
        } catch (_: Exception) {
            Result.failure(AuthException(AuthError.UNKNOWN))
        }
    }

    override suspend fun loginWithGoogle(idToken: String?): Result<Unit> {
        if (idToken.isNullOrBlank()) {
            return Result.failure(AuthException(AuthError.NOT_SUPPORTED))
        }

        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).await()
            Result.success(Unit)
        } catch (_: Exception) {
            Result.failure(AuthException(AuthError.UNKNOWN))
        }
    }

    override suspend fun loginAsGuest(): Result<Unit> {
        return try {
            auth.signInAnonymously().await()
            Result.success(Unit)
        } catch (_: Exception) {
            Result.failure(AuthException(AuthError.UNKNOWN))
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            auth.signOut()
            Result.success(Unit)
        } catch (_: Exception) {
            Result.failure(AuthException(AuthError.UNKNOWN))
        }
    }
}
