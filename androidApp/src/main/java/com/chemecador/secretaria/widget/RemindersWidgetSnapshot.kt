package com.chemecador.secretaria.widget

import com.chemecador.secretaria.reminders.Reminder
import com.chemecador.secretaria.reminders.ReminderDue
import com.chemecador.secretaria.reminders.ReminderKey
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lo que el widget tiene que pintar, ya recortado y ordenado, sin depender de Firestore.
 *
 * Es una copia local y no una vista de la base de datos: el widget se dibuja en cuanto el
 * lanzador lo pide, y una consulta de red en ese momento dejaria un hueco en blanco. El refresco
 * ocurre despues y vuelve a pintar.
 */
internal data class RemindersWidgetSnapshot(
    val items: List<RemindersWidgetItem> = emptyList(),
    /** Nulo mientras no se haya cargado nunca: no es lo mismo que "no tienes recordatorios". */
    val updatedAtEpochMillis: Long? = null,
    val isSignedIn: Boolean = true,
) {
    fun toJson(): String = JSONObject()
        .put(KEY_VERSION, CURRENT_VERSION)
        .put(KEY_UPDATED_AT, updatedAtEpochMillis ?: JSONObject.NULL)
        .put(KEY_SIGNED_IN, isSignedIn)
        .put(KEY_ITEMS, JSONArray().apply { items.forEach { put(it.toJson()) } })
        .toString()

    companion object {
        /** Un formato viejo se descarta entero: es una cache, se vuelve a llenar sola. */
        private const val CURRENT_VERSION = 1
        private const val KEY_ITEMS = "items"
        private const val KEY_SIGNED_IN = "signedIn"
        private const val KEY_UPDATED_AT = "updatedAt"
        private const val KEY_VERSION = "version"

        fun fromJson(json: String): RemindersWidgetSnapshot? {
            val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
            if (root.optInt(KEY_VERSION) != CURRENT_VERSION) return null
            val array = root.optJSONArray(KEY_ITEMS) ?: JSONArray()
            return RemindersWidgetSnapshot(
                items = (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let(RemindersWidgetItem::fromJson)
                },
                updatedAtEpochMillis = root.optLong(KEY_UPDATED_AT).takeIf { !root.isNull(KEY_UPDATED_AT) },
                isSignedIn = root.optBoolean(KEY_SIGNED_IN, true),
            )
        }
    }
}

/**
 * [order] viaja porque completar desde el widget lo conserva, igual que hace
 * `RemindersViewModel.setReminderCompleted`.
 */
internal data class RemindersWidgetItem(
    val ownerId: String,
    val id: String,
    val text: String,
    val order: Int,
    val dueDate: String? = null,
    val dueTime: String? = null,
    val isShared: Boolean = false,
) {
    val key: ReminderKey get() = ReminderKey(ownerId, id)

    /**
     * El vencimiento se guarda como texto y se interpreta al pintar, no al guardar: la copia
     * local puede tener horas y lo vencido se calcula contra el reloj de ahora mismo.
     */
    val due: ReminderDue?
        get() {
            val date = dueDate?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
                ?: return null
            val time = dueTime?.let { value -> runCatching { LocalTime.parse(value) }.getOrNull() }
            return ReminderDue(date, time)
        }

    fun toJson(): JSONObject = JSONObject()
        .put(KEY_OWNER_ID, ownerId)
        .put(KEY_ID, id)
        .put(KEY_TEXT, text)
        .put(KEY_ORDER, order)
        .put(KEY_DUE_DATE, dueDate ?: JSONObject.NULL)
        .put(KEY_DUE_TIME, dueTime ?: JSONObject.NULL)
        .put(KEY_SHARED, isShared)

    companion object {
        private const val KEY_DUE_DATE = "dueDate"
        private const val KEY_DUE_TIME = "dueTime"
        private const val KEY_ID = "id"
        private const val KEY_ORDER = "order"
        private const val KEY_OWNER_ID = "ownerId"
        private const val KEY_SHARED = "shared"
        private const val KEY_TEXT = "text"

        fun fromJson(json: JSONObject): RemindersWidgetItem? {
            val ownerId = json.optString(KEY_OWNER_ID).takeIf { it.isNotBlank() } ?: return null
            val id = json.optString(KEY_ID).takeIf { it.isNotBlank() } ?: return null
            return RemindersWidgetItem(
                ownerId = ownerId,
                id = id,
                text = json.optString(KEY_TEXT),
                order = json.optInt(KEY_ORDER),
                dueDate = json.optString(KEY_DUE_DATE).takeIf { !json.isNull(KEY_DUE_DATE) },
                dueTime = json.optString(KEY_DUE_TIME).takeIf { !json.isNull(KEY_DUE_TIME) },
                isShared = json.optBoolean(KEY_SHARED),
            )
        }
    }
}

internal fun Reminder.toWidgetItem(): RemindersWidgetItem = RemindersWidgetItem(
    ownerId = ownerId,
    id = id,
    text = text,
    order = order,
    dueDate = due?.date?.toString(),
    dueTime = due?.time?.toString(),
    isShared = isShared,
)
