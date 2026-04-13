package com.chemecador.secretaria.login

enum class AuthError {
    INVALID_USER,
    WRONG_PASSWORD,
    USER_ALREADY_EXISTS,
    WEAK_PASSWORD,
    INVALID_EMAIL,
    CANCELLED,
    NOT_SUPPORTED,
    UNKNOWN,
}

class AuthException(val error: AuthError) : Exception(error.name)
