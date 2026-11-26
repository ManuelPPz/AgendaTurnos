package com.example.agendaturnos.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agendaturnos.data.AgendaRepository
import com.example.agendaturnos.data.model.TratamientoCatalogo
import com.example.agendaturnos.lector.Borrador
import com.example.agendaturnos.lector.DatosBorrador
import com.example.agendaturnos.ui.components.DialogoChoque
import com.example.agendaturnos.ui.components.EditorTurno
import com.example.agendaturnos.ui.components.TurnoEditorState
import com.example.agendaturnos.ui.formatoFechaLarga
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/** Agendar un turno a un cliente que ya existe. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendarScreen(
    repo: AgendaRepository,
    clienteIdInicial: Long,
    onVolver: () -> Unit,
    onCreado: (turnoId: Long) -> Unit,
) {
    var clienteId by rememberSaveable { mutableStateOf(clienteIdInicial) }
    // Datos leídos de un mensaje de WhatsApp, si se llegó desde "Agendar desde mensaje"
    val borrador = remember { Borrador.tomar() }
    val catalogo by remember { repo.tratamientosActivos() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agendar turno") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        if (clienteId == 0L) {
            SelectorCliente(repo, Modifier.padding(padding)) { clienteId = it }
        } else {
            FormularioAgenda(
                repo = repo,
                clienteId = clienteId,
                catalogo = catalogo,
                borrador = borrador,
                modifier = Modifier.padding(padding),
                onCambiarCliente = { clienteId = 0L },
                onCreado = onCreado,
            )
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
private fun SelectorCliente(repo: AgendaRepository, modifier: Modifier, onElegir: (Long) -> Unit) {
    var texto by rememberSaveable { mutableStateOf("") }
    val textoFlow = remember { MutableStateFlow(texto) }
    LaunchedEffect(texto) { textoFlow.value = texto }
    val resultados by remember {
        textoFlow.debounce(150).flatMapLatest { repo.buscarClientes(it) }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = texto,
            onValueChange = { texto = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("¿A quién agendás?") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (texto.isNotEmpty()) {
                    IconButton(onClick = { texto = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda")
                    }
                }
            },
            singleLine = true,
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(resultados, key = { it.id }) { c ->
                ListItem(
                    headlineContent = { Text(c.nombre) },
                    supportingContent = { Text(c.telefono) },
                    modifier = Modifier.clickable { onElegir(c.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun FormularioAgenda(
    repo: AgendaRepository,
    clienteId: Long,
    catalogo: List<TratamientoCatalogo>,
    borrador: DatosBorrador?,
    modifier: Modifier,
    onCambiarCliente: () -> Unit,
    onCreado: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val cliente by remember(clienteId) { repo.observarCliente(clienteId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val ultimo by remember(clienteId) { repo.ultimoTerminadoDeCliente(clienteId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val turno = rememberSaveable(clienteId, saver = TurnoEditorState.Guardador) { borrador?.aEstadoTurno() ?: TurnoEditorState() }

    var intento by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var mostrarChoque by remember { mutableStateOf(false) }

    fun guardar() {
        guardando = true
        scope.launch {
            val id = repo.agendar(turno.aTurno(null, clienteId), turno.aItems())
            guardando = false
            onCreado(id)
        }
    }

    fun confirmar() {
        intento = true
        if (turno.errores().isNotEmpty() || guardando) return
        scope.launch {
            if (repo.hayTurnoEnMismoHorario(requireNotNull(turno.fechaHora))) mostrarChoque = true else guardar()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val c = cliente
        if (c != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(c.nombre, style = MaterialTheme.typography.titleMedium)
                    Text(c.telefono)
                    c.direccion?.let { Text(it) }
                    c.preferencias?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val u = ultimo
                    Text(
                        if (u != null) "Último: ${u.resumenTratamientos}, el ${u.turno.fechaHora.format(formatoFechaLarga)}"
                        else "Sin visitas anteriores",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row {
                        if (u != null) {
                            TextButton(onClick = { turno.repetir(u, catalogo) }) { Text("Repetir último") }
                        }
                        TextButton(onClick = onCambiarCliente) { Text("Cambiar cliente") }
                    }
                }
            }
        }

        EditorTurno(
            estado = turno,
            catalogo = catalogo,
            direccionCliente = c?.direccion,
            mostrarErrores = intento,
        )

        Button(
            onClick = { confirmar() },
            enabled = !guardando,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Confirmar turno") }
    }

    if (mostrarChoque) {
        DialogoChoque(
            fechaHora = requireNotNull(turno.fechaHora),
            onConfirmar = { mostrarChoque = false; guardar() },
            onCancelar = { mostrarChoque = false },
        )
    }
}
