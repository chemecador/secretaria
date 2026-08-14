package com.chemecador.secretaria.reminders

import androidx.compose.material3.AlertDialog
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.action_accept
import secretaria.composeapp.generated.resources.cancel
import kotlin.time.Instant

/**
 * Calendario estandar, sin contenido propio anadido: meter controles en el slot del
 * [DatePickerDialog] los solapaba con la cabecera del mes. La hora se elige aparte.
 *
 * `selectedDateMillis` viene siempre en medianoche UTC, asi que la conversion tiene que hacerse
 * con [TimeZone.UTC]: usar la zona del dispositivo devuelve el dia anterior en offsets negativos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderDatePickerDialog(
    initialDate: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate
            ?.atStartOfDayIn(TimeZone.UTC)
            ?.toEpochMilliseconds(),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onConfirm(millis.toUtcLocalDate())
                    }
                },
                enabled = datePickerState.selectedDateMillis != null,
            ) {
                Text(stringResource(Res.string.action_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    ) {
        // Sin cabecera para que el dialogo no ocupe casi toda la pantalla: el dia marcado y el
        // boton de aceptar ya indican la seleccion.
        DatePicker(
            state = datePickerState,
            title = null,
            headline = null,
            showModeToggle = false,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderTimePickerDialog(
    initialTime: LocalTime?,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime?.hour ?: DEFAULT_REMINDER_HOUR,
        initialMinute = initialTime?.minute ?: 0,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        text = { TimePicker(state = timePickerState) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(LocalTime(timePickerState.hour, timePickerState.minute)) },
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

private const val DEFAULT_REMINDER_HOUR = 9

private fun Long.toUtcLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
