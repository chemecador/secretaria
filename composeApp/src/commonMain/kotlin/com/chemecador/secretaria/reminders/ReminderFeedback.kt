package com.chemecador.secretaria.reminders

data class ReminderFeedback(
    val action: ReminderFeedbackAction,
    val isSuccess: Boolean,
)

enum class ReminderFeedbackAction {
    COMPLETED,
    RESTORED,
    DELETED,
}
