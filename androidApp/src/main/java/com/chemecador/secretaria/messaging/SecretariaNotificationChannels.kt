package com.chemecador.secretaria.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.chemecador.secretaria.R

object SecretariaNotificationChannels {
    const val LIST_SHARED_CHANNEL_ID = "list_shared"
    const val FRIEND_REQUESTS_CHANNEL_ID = "friend_requests"
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
    }

    private fun createChannel(
        context: Context,
        manager: NotificationManager,
        channelId: String,
        nameResId: Int,
        descriptionResId: Int,
    ) {
        if (manager.getNotificationChannel(channelId) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                context.getString(nameResId),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(descriptionResId)
            },
        )
    }
}
