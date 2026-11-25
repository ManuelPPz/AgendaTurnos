package com.example.agendaturnos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.agendaturnos.data.AgendaRepository
import com.example.agendaturnos.data.model.Cliente
import com.example.agendaturnos.data.model.Genero
import com.example.agendaturnos.ui.etiqueta
import com.example.agendaturnos.ui.formatoFechaCorta
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Alta (clienteId = 0) o edición de un cliente. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClienteFormScreen(
    repo: AgendaRepository,
    clienteId: Long,
    onVolver: () -> Unit,
    onGuardado: (Long) -> Unit,
    onVerCliente: (Long) -> Unit,
    onArchivado: () -> Unit,
) {
    val esNuevo = clienteId == 0L
    val scope = rememberCoroutineScope()

    var original by remember { mutableStateOf<Cliente?>(null) }
    var cargado by rememberSaveable { mutableStateOf(esNuevo) }
    var nombre by rememberSaveable { mutableStateOf("") }
    var telefono by rememberSaveable { mutableStateOf("") }
    var direccion by rememberSaveable { mutableStateOf("") }
    var preferencias by rememberSaveable { mutableStateOf("") }
    var genero by rememberSaveable { mutableStateOf<Genero?>(null) }
    var nacimiento by rememberSaveable { mutableStateOf<String?>(null) } // fecha ISO "aaaa-mm-dd"

    var intentoGuardar by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var duplicados by remember { mutableStateOf<List<Cliente>>(emptyList()) }
    var mostrarCalendario by remember { mutableStateOf(false) }
    var confirmarArchivar by remember { mutableStateOf(false) }

    LaunchedEffect(clienteId) {
        if (!esNuevo) {
            val c = repo.obtenerCliente(clienteId)
            original = c
            if (!cargado && c != null) {
                nombre = c.nombre
                telefono = c.telefono
                direccion = c.direccion.orEmpty()
                preferencias = c.preferencias.orEmpty()
                genero = c.genero
                nacimiento = c.fechaNacimiento?.toString()
                cargado = true
            }
        }
    }

    val nombreInvalido = nombre.isBlank()
    val telefonoInvalido = telefono.count(Char::isDigit) < 6

    fun armarCliente(): Cliente =
        (original ?: Cliente(nombre = "", telefono = "")).copy(
            nombre = nombre.trim(),
            telefono = telefono.trim(),
            direccion = direccion.trim().ifBlank { null },
            genero = genero,
            fechaNacimiento = nacimiento?.let(LocalDate::parse),
            preferencias = preferencias.trim().ifBlank { null },
        )

    fun guardar(aunqueHayaDuplicados: Boolean) {
        intentoGuardar = true
        if (nombreInvalido || telefonoInvalido || guardando) return
        scope.launch {
            guardando = true
            if (esNuevo && !aunqueHayaDuplicados) {
                val parecidos = repo.posiblesDuplicados(nombre, telefono)
                if (parecidos.isNotEmpty()) {
                    duplicados = parecidos
                    guardando = false
                    return@launch
                }
            }
            val id = repo.guardarCliente(armarCliente())
            guardando = false
            onGuardado(id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (esNuevo) "Nuevo cliente" else "Editar cliente") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre *") },
                isError = intentoGuardar && nombreInvalido,
                supportingText = { if (intentoGuardar && nombreInvalido) Text("Obligatorio") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = telefono,
                onValueChange = { telefono = it },
                label = { Text("Teléfono *") },
                isError = intentoGuardar && telefonoInvalido,
                supportingText = { if (intentoGuardar && telefonoInvalido) Text("Ingresá un teléfono válido") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = direccion,
                onValueChange = { direccion = it },
                label = { Text("Dirección de atención (opcional)") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Género", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Genero.entries.forEach { g ->
                    FilterChip(
                        selected = genero == g,
                        onClick = { genero = if (genero == g) null else g },
                        label = { Text(g.etiqueta()) },
                    )
                }
            }

            OutlinedTextField(
                value = nacimiento?.let { LocalDate.parse(it).format(formatoFechaCorta) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Fecha de nacimiento (opcional)") },
                trailingIcon = {
                    Row {
                        if (nacimiento != null) {
                            IconButton(onClick = { nacimiento = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Quitar fecha")
                            }
                        }
                        IconButton(onClick = { mostrarCalendario = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Elegir fecha")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = preferencias,
                onValueChange = { preferencias = it },
                label = { Text("Preferencias (opcional)") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { guardar(aunqueHayaDuplicados = false) },
                enabled = !guardando,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar") }

            if (!esNuevo && original?.activo == true) {
                TextButton(
                    onClick = { confirmarArchivar = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Archivar cliente") }
            }
        }
    }

    if (mostrarCalendario) {
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = nacimiento?.let {
                LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            },
            initialDisplayMode = DisplayMode.Input,
        )
        DatePickerDialog(
            onDismissRequest = { mostrarCalendario = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { millis ->
                        nacimiento = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    mostrarCalendario = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarCalendario = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(state = estado)
        }
    }

    if (duplicados.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { duplicados = emptyList() },
            title = { Text("¿Ya está cargado?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Encontré clientes con el mismo nombre o teléfono:")
                    duplicados.forEach { Text("• ${it.nombre} — ${it.telefono}") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    duplicados = emptyList()
                    guardar(aunqueHayaDuplicados = true)
                }) { Text("Guardar igual") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val existente = duplicados.first().id
                    duplicados = emptyList()
                    onVerCliente(existente)
                }) { Text("Ver existente") }
            },
        )
    }

    if (confirmarArchivar) {
        AlertDialog(
            onDismissRequest = { confirmarArchivar = false },
            title = { Text("¿Archivar a ${nombre.trim()}?") },
            text = { Text("No va a aparecer en las búsquedas. Su historial se conserva.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarArchivar = false
                    scope.launch {
                        repo.archivarCliente(clienteId)
                        onArchivado()
                    }
                }) { Text("Archivar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarArchivar = false }) { Text("Cancelar") }
            },
        )
    }
}
