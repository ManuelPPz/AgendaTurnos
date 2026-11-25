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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agendaturnos.data.AgendaRepository
import com.example.agendaturnos.data.model.Cliente
import com.example.agendaturnos.data.model.Genero
import com.example.agendaturnos.ui.components.DialogoChoque
import com.example.agendaturnos.ui.components.DialogoFecha
import com.example.agendaturnos.ui.components.EditorTurno
import com.example.agendaturnos.ui.components.TurnoEditorState
import com.example.agendaturnos.ui.etiqueta
import com.example.agendaturnos.ui.formatoFechaCorta
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Alta de un cliente nuevo junto con su primer turno. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuevoClienteTurnoScreen(
    repo: AgendaRepository,
    onVolver: () -> Unit,
    onCreado: (turnoId: Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val catalogo by remember { repo.tratamientosActivos() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var nombre by rememberSaveable { mutableStateOf("") }
    var telefono by rememberSaveable { mutableStateOf("") }
    var direccion by rememberSaveable { mutableStateOf("") }
    var preferencias by rememberSaveable { mutableStateOf("") }
    var genero by rememberSaveable { mutableStateOf<Genero?>(null) }
    var nacimiento by rememberSaveable { mutableStateOf<String?>(null) }
    val turno = rememberSaveable(saver = TurnoEditorState.Guardador) { TurnoEditorState() }

    var intento by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var elegirNacimiento by remember { mutableStateOf(false) }
    var duplicados by remember { mutableStateOf<List<Cliente>>(emptyList()) }
    var mostrarChoque by remember { mutableStateOf(false) }
    // null = crear cliente nuevo; un id = agendar a ese cliente existente
    var clienteDestino by remember { mutableStateOf<Long?>(null) }

    val nombreInvalido = nombre.isBlank()
    val telefonoInvalido = telefono.count(Char::isDigit) < 6

    fun armarCliente() = Cliente(
        nombre = nombre.trim(),
        telefono = telefono.trim(),
        direccion = direccion.trim().ifBlank { null },
        genero = genero,
        fechaNacimiento = nacimiento?.let(LocalDate::parse),
        preferencias = preferencias.trim().ifBlank { null },
    )

    fun guardar() {
        guardando = true
        scope.launch {
            val existente = clienteDestino
            val turnoId = if (existente != null) {
                repo.agendar(turno.aTurno(null, existente), turno.aItems())
            } else {
                repo.crearClienteConTurno(armarCliente(), turno.aTurno(null, 0L), turno.aItems())
            }
            guardando = false
            onCreado(turnoId)
        }
    }

    fun verificarHorario() {
        scope.launch {
            if (repo.hayTurnoEnMismoHorario(requireNotNull(turno.fechaHora))) mostrarChoque = true else guardar()
        }
    }

    fun confirmar() {
        intento = true
        if (nombreInvalido || telefonoInvalido || turno.errores().isNotEmpty() || guardando) return
        scope.launch {
            val parecidos = repo.posiblesDuplicados(nombre, telefono)
            if (parecidos.isNotEmpty()) {
                duplicados = parecidos
            } else {
                clienteDestino = null
                verificarHorario()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo cliente + turno") },
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
            Text("Cliente", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre *") },
                isError = intento && nombreInvalido,
                supportingText = { if (intento && nombreInvalido) Text("Obligatorio") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = telefono,
                onValueChange = { telefono = it },
                label = { Text("Teléfono *") },
                isError = intento && telefonoInvalido,
                supportingText = { if (intento && telefonoInvalido) Text("Ingresá un teléfono válido") },
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
                        IconButton(onClick = { elegirNacimiento = true }) {
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
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            Text("Turno", style = MaterialTheme.typography.titleMedium)
            EditorTurno(
                estado = turno,
                catalogo = catalogo,
                direccionCliente = direccion.trim().ifBlank { null },
                mostrarErrores = intento,
            )

            Button(
                onClick = { confirmar() },
                enabled = !guardando,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Confirmar") }
        }
    }

    if (elegirNacimiento) {
        DialogoFecha(
            inicial = nacimiento?.let(LocalDate::parse),
            escribirFecha = true,
            onElegir = { nacimiento = it.toString() },
            onCerrar = { elegirNacimiento = false },
        )
    }

    if (duplicados.isNotEmpty()) {
        val primero = duplicados.first()
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
                    clienteDestino = primero.id
                    verificarHorario()
                }) { Text("Agendar a ${primero.nombre}") }
            },
            dismissButton = {
                TextButton(onClick = {
                    duplicados = emptyList()
                    clienteDestino = null
                    verificarHorario()
                }) { Text("Crear nuevo igual") }
            },
        )
    }

    if (mostrarChoque) {
        DialogoChoque(
            fechaHora = requireNotNull(turno.fechaHora),
            onConfirmar = { mostrarChoque = false; guardar() },
            onCancelar = { mostrarChoque = false },
        )
    }
}
