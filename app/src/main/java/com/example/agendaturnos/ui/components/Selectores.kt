package com.example.agendaturnos.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Calendario para elegir una fecha.
 * [escribirFecha] = true abre en modo texto (cómodo para fechas de nacimiento).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoFecha(
    inicial: LocalDate?,
    escribirFecha: Boolean,
    onElegir: (LocalDate) -> Unit,
    onCerrar: () -> Unit,
) {
    val desde: LocalDate? = inicial ?: if (escribirFecha) null else LocalDate.now()
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = desde?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        initialDisplayMode = if (escribirFecha) DisplayMode.Input else DisplayMode.Picker,
    )
    DatePickerDialog(
        onDismissRequest = onCerrar,
        confirmButton = {
            TextButton(onClick = {
                estado.selectedDateMillis?.let { millis ->
                    onElegir(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onCerrar()
            }) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    ) {
        DatePicker(state = estado)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoHora(
    inicial: LocalTime?,
    onElegir: (LocalTime) -> Unit,
    onCerrar: () -> Unit,
) {
    val estado = rememberTimePickerState(
        initialHour = inicial?.hour ?: 9,
        initialMinute = inicial?.minute ?: 0,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onCerrar,
        confirmButton = {
            TextButton(onClick = {
                onElegir(LocalTime.of(estado.hour, estado.minute))
                onCerrar()
            }) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
        text = { TimePicker(state = estado) },
    )
}
