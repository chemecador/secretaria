package com.chemecador.secretaria.messaging

import android.content.Intent
import com.chemecador.secretaria.OpenListRequest

object NotificationOpenListIntent {
    const val ACTION_OPEN_LIST = "com.chemecador.secretaria.OPEN_LIST"
    const val EXTRA_LIST_ID = "openListId"
    const val EXTRA_LIST_NAME = "openListName"
    const val EXTRA_LIST_ORDERED = "openListOrdered"
    const val EXTRA_OWNER_ID = "openListOwnerId"

    fun Intent.toOpenListRequest(): OpenListRequest? {
        val ownerId = getStringExtra(EXTRA_OWNER_ID).orEmpty()
        val listId = getStringExtra(EXTRA_LIST_ID).orEmpty()
        val listName = getStringExtra(EXTRA_LIST_NAME).orEmpty()
        if (ownerId.isBlank() || listId.isBlank() || listName.isBlank()) return null
        return OpenListRequest(
            ownerId = ownerId,
            listId = listId,
            listName = listName,
            isOrdered = getBooleanExtra(EXTRA_LIST_ORDERED, false),
        )
    }
}
