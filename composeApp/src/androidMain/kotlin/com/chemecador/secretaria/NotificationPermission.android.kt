package com.chemecador.secretaria

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

@Composable
internal actual fun rememberNotificationPermissionController(): NotificationPermissionController? {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return null
    // El resultado se ignora a proposito: el estado se le vuelve a preguntar al sistema.
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result ignored */ }

    return remember(activity, requestPermission) {
        object : NotificationPermissionController {

            override fun areNotificationsEnabled(): Boolean =
                NotificationManagerCompat.from(activity).areNotificationsEnabled()

            override fun requestNotifications() {
                if (activity.canAskForNotificationPermission()) {
                    requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    activity.openNotificationSettings()
                }
            }
        }
    }
}

/**
 * `shouldShowRequestPermissionRationale` es lo que separa "denegado una vez" de "denegado para
 * siempre". `MainActivity` ya pide el permiso en cada arranque, asi que si el sistema ya no deja
 * preguntar el unico camino que queda son los ajustes. Antes de Android 13 no hay permiso que
 * pedir: las notificaciones solo se reactivan desde ahi.
 */
private fun Activity.canAskForNotificationPermission(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val granted = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    return !granted && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
}

private fun Activity.openNotificationSettings() {
    val notificationSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    // Algunos fabricantes no resuelven la pantalla de notificaciones; la ficha de la app si.
    if (runCatching { startActivity(notificationSettings) }.isFailure) {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null),
                ),
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
