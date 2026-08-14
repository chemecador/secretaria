package com.chemecador.secretaria.reminders

data class ReminderFeedback(
    val action: ReminderFeedbackAction,
    val isSuccess: Boolean,
)

enum class ReminderFeedbackAction {
    COMPLETED,
    RESTORED,
    DELETED,
    LEFT_SHARED,
}

data class ReminderSharingFeedback(
    val friendName: String,
    val action: ReminderSharingAction,
)

enum class ReminderSharingAction {
    SHARED,
    UNSHARED,
}
