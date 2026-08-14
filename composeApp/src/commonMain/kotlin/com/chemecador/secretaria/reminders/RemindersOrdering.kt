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

internal fun List<Reminder>.applyReminderOrder(
    reminderKeysInOrder: List<ReminderKey>,
): List<Reminder>? {
    if (size != reminderKeysInOrder.size || reminderKeysInOrder.distinct().size != size) {
        return null
    }

    val remindersByKey = associateBy(Reminder::key)
    if (remindersByKey.size != size || reminderKeysInOrder.any { it !in remindersByKey }) {
        return null
    }

    return reminderKeysInOrder.mapIndexed { index, reminderKey ->
        val reminder = remindersByKey.getValue(reminderKey)
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
