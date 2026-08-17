package com.chemecador.secretaria.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.chemecador.secretaria.R

object SecretariaNotificationChannels {
    const val LIST_SHARED_CHANNEL_ID = "list_shared"
    const val FRIEND_REQUESTS_CHANNEL_ID = "friend_requests"
    const val REMINDER_SHARED_CHANNEL_ID = "reminder_shared"
    const val REMINDER_DUE_CHANNEL_ID = "reminder_due"
    const val DEFAULT_CHANNEL_ID = LIST_SHARED_CHANNEL_ID

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(
            context = context,
            manager = manager,
            channelId = LIST_SHARED_CHANNEL_ID,
            nameResId = R.string.channel_list_shared_name,
            descriptionResId = R.string.channel_list_shared_desc,
        )
        createChannel(
            context = context,
            manager = manager,
            channelId = FRIEND_REQUESTS_CHANNEL_ID,
            nameResId = R.string.channel_friend_requests_name,
            descriptionResId = R.string.channel_friend_requests_desc,
        )
        createChannel(
            context = context,
            manager = manager,
            channelId = REMINDER_SHARED_CHANNEL_ID,
            nameResId = R.string.channel_reminder_shared_name,
            descriptionResId = R.string.channel_reminder_shared_desc,
        )
        // Canal propio para que el usuario pueda silenciar los avisos de vencimiento sin
        // perder los de compartidos, que son mucho menos frecuentes.
        createChannel(
            context = context,
            manager = manager,
            channelId = REMINDER_DUE_CHANNEL_ID,
            nameResId = R.string.channel_reminder_due_name,
            descriptionResId = R.string.channel_reminder_due_desc,
            importance = NotificationManager.IMPORTANCE_HIGH,
        )
    }

    private fun createChannel(
        context: Context,
        manager: NotificationManager,
        channelId: String,
        nameResId: Int,
        descriptionResId: Int,
        importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
    ) {
        if (manager.getNotificationChannel(channelId) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                context.getString(nameResId),
                importance,
            ).apply {
                description = context.getString(descriptionResId)
            },
        )
    }
}
