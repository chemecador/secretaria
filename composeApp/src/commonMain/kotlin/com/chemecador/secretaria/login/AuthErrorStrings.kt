package com.chemecador.secretaria.login

import org.jetbrains.compose.resources.StringResource
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.error_invalid_email
import secretaria.composeapp.generated.resources.error_invalid_user
import secretaria.composeapp.generated.resources.error_login_cancelled
import secretaria.composeapp.generated.resources.error_not_supported
import secretaria.composeapp.generated.resources.error_unknown
import secretaria.composeapp.generated.resources.error_user_already_exists
import secretaria.composeapp.generated.resources.error_weak_password
import secretaria.composeapp.generated.resources.error_wrong_password

/**
 * El mapeo vive suelto, no en una pantalla, porque el acceso se reparte entre la eleccion de
 * metodo y el formulario de email y los dos resuelven el mismo [AuthError].
 */
internal fun AuthError.toStringRes(): StringResource = when (this) {
    AuthError.INVALID_USER -> Res.string.error_invalid_user
    AuthError.WRONG_PASSWORD -> Res.string.error_wrong_password
    AuthError.USER_ALREADY_EXISTS -> Res.string.error_user_already_exists
    AuthError.WEAK_PASSWORD -> Res.string.error_weak_password
    AuthError.INVALID_EMAIL -> Res.string.error_invalid_email
    AuthError.CANCELLED -> Res.string.error_login_cancelled
    AuthError.NOT_SUPPORTED -> Res.string.error_not_supported
    AuthError.UNKNOWN -> Res.string.error_unknown
}
