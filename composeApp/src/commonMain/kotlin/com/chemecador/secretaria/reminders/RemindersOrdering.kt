package com.chemecador.secretaria.reminders

internal fun List<Reminder>.moveReminder(fromIndex: Int, toIndex: Int): List<Reminder> {
    if (fromIndex !in indices || toIndex !in indices || fromIndex == toIndex) {
        return normalizeReminderOrder()
    }

    val mutableReminders = toMutableList()
    val movedReminder = mutableReminders.removeAt(fromIndex)
    mutableReminders.add(toIndex, movedReminder)
    return mutableReminders.normalizeReminderOrder()
}

internal fun List<Reminder>.applyReminderOrder(reminderIdsInOrder: List<String>): List<Reminder>? {
    if (size != reminderIdsInOrder.size || reminderIdsInOrder.distinct().size != size) {
        return null
    }

    val remindersById = associateBy(Reminder::id)
    if (remindersById.size != size || reminderIdsInOrder.any { it !in remindersById }) {
        return null
    }

    return reminderIdsInOrder.mapIndexed { index, reminderId ->
        val reminder = remindersById.getValue(reminderId)
        if (reminder.order == index) {
            reminder
        } else {
            reminder.copy(order = index)
        }
    }
}

internal fun List<Reminder>.normalizeReminderOrder(): List<Reminder> =
    mapIndexed { index, reminder ->
        if (reminder.order == index) {
            reminder
        } else {
            reminder.copy(order = index)
        }
    }
