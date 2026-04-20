package com.chemecador.secretaria.messaging

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chemecador.secretaria.MainActivity
import com.chemecador.secretaria.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SecretariaMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data[DATA_TITLE]
        val body = message.notification?.body ?: message.data[DATA_BODY]
        if (title.isNullOrBlank() || body.isNullOrBlank()) return

        val context = applicationContext
        SecretariaNotificationChannels.ensureCreated(context)
        val channelId = message.data[DATA_CHANNEL_ID]
            ?.takeIf { it.isNotBlank() }
            ?: SecretariaNotificationChannels.DEFAULT_CHANNEL_ID

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        createContentIntent(context)?.let(builder::setContentIntent)

        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) {
            manager.notify(notificationIdFor(message), builder.build())
        }
    }

    override fun onNewToken(token: String) {
        if (token.isBlank()) return
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection(USERS)
            .document(userId)
            .collection(FCM_TOKENS)
            .document(token)
            .set(
                mapOf(
                    "token" to token,
                    "platform" to "android",
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
    }

    private fun createContentIntent(context: Context): PendingIntent? {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationIdFor(message: RemoteMessage): Int {
        val source = message.data[DATA_NOTIFICATION_TAG]
            ?: message.messageId
            ?: System.currentTimeMillis().toString()
        return source.hashCode() and Int.MAX_VALUE
    }

    private companion object {
        const val DATA_BODY = "body"
        const val DATA_CHANNEL_ID = "channelId"
        const val DATA_NOTIFICATION_TAG = "notificationTag"
        const val DATA_TITLE = "title"
        const val FCM_TOKENS = "fcm_tokens"
        const val USERS = "users"
    }
}
