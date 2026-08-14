package com.chemecador.secretaria.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.action_accept
import secretaria.composeapp.generated.resources.cancel
import secretaria.composeapp.generated.resources.reminder_due_add_time
import secretaria.composeapp.generated.resources.reminder_due_clear
import kotlin.time.Instant

/**
 * Elige un vencimiento flotante. [onConfirm] recibe `null` cuando el usuario quita la fecha.
 *
 * `selectedDateMillis` viene siempre en medianoche UTC, asi que la conversion tiene que hacerse
 * con [TimeZone.UTC]: usar la zona del dispositivo devuelve el dia anterior en offsets negativos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderDuePickerDialog(
    initialDue: ReminderDue?,
    onDismiss: () -> Unit,
    onConfirm: (ReminderDue?) -> Unit,
) {
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    var wantsTime by remember { mutableStateOf(initialDue?.time != null) }

    val currentPickedDate = pickedDate
    if (currentPickedDate == null) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDue?.date
                ?.atStartOfDayIn(TimeZone.UTC)
                ?.toEpochMilliseconds(),
        )

        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedDate = datePickerState.selectedDateMillis?.toUtcLocalDate()
                            ?: return@TextButton
                        if (wantsTime) {
                            pickedDate = selectedDate
                        } else {
                            onConfirm(ReminderDue(selectedDate))
                        }
                    },
                    enabled = datePickerState.selectedDateMillis != null,
                ) {
                    Text(stringResource(Res.string.action_accept))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (initialDue != null) {
                        TextButton(onClick = { onConfirm(null) }) {
                            Text(stringResource(Res.string.reminder_due_clear))
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.cancel))
                    }
                }
            },
        ) {
            DatePicker(
                state = datePickerState,
                title = null,
                headline = null,
                showModeToggle = false,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = wantsTime, onCheckedChange = { wantsTime = it })
                Text(
                    text = stringResource(Res.string.reminder_due_add_time),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    } else {
        val timePickerState = rememberTimePickerState(
            initialHour = initialDue?.time?.hour ?: 9,
            initialMinute = initialDue?.time?.minute ?: 0,
            is24Hour = true,
        )

        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm(
                            ReminderDue(
                                date = currentPickedDate,
                                time = LocalTime(timePickerState.hour, timePickerState.minute),
                            ),
                        )
                    },
                ) {
                    Text(stringResource(Res.string.action_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

private fun Long.toUtcLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
