package com.chemecador.secretaria

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.chemecador.secretaria.messaging.NotificationOpenListIntent.toOpenListRequest
import com.chemecador.secretaria.messaging.NotificationOpenRemindersIntent.isOpenRemindersRequest
import com.chemecador.secretaria.messaging.SecretariaNotificationChannels
import com.chemecador.secretaria.widget.RemindersWidgetUpdater

class MainActivity : ComponentActivity() {

    private var pendingOpenListRequest by mutableStateOf<OpenListRequest?>(null)
    private var pendingOpenRemindersRequest by mutableStateOf(false)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        SecretariaNotificationChannels.ensureCreated(this)
        maybeRequestNotificationPermission()
        pendingOpenListRequest = intent.toOpenListRequest()
        pendingOpenRemindersRequest = intent.isOpenRemindersRequest()

        setContent {
            App(
                googleServerClientId = getString(R.string.default_web_client_id),
                openListRequest = pendingOpenListRequest,
                onOpenListRequestConsumed = { pendingOpenListRequest = null },
                openRemindersRequest = pendingOpenRemindersRequest,
                onOpenRemindersRequestConsumed = { pendingOpenRemindersRequest = false },
            )
        }
    }

    /**
     * Al salir de la app es cuando el widget tiene mas posibilidades de estar desfasado: el
     * usuario acaba de crear, editar o completar algo. Aqui no se sabe que cambio, asi que el
     * widget vuelve a leer; es una consulta por salida, no por cada escritura.
     *
     * Un giro de pantalla tambien pasa por aqui y no cambia nada, asi que se descarta.
     */
    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        RemindersWidgetUpdater.refresh(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpenListRequest = intent.toOpenListRequest()
        pendingOpenRemindersRequest = intent.isOpenRemindersRequest()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
