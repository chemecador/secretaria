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
import com.chemecador.secretaria.messaging.SecretariaNotificationChannels

class MainActivity : ComponentActivity() {

    private var pendingOpenListRequest by mutableStateOf<OpenListRequest?>(null)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        SecretariaNotificationChannels.ensureCreated(this)
        maybeRequestNotificationPermission()
        pendingOpenListRequest = intent.toOpenListRequest()

        setContent {
            App(
                googleServerClientId = getString(R.string.default_web_client_id),
                openListRequest = pendingOpenListRequest,
                onOpenListRequestConsumed = { pendingOpenListRequest = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpenListRequest = intent.toOpenListRequest()
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
