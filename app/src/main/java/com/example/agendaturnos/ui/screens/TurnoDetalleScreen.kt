package com.example.agendaturnos.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agendaturnos.data.AgendaRepository
import com.example.agendaturnos.data.model.EstadoTurno
import com.example.agendaturnos.data.model.Lugar
import com.example.agendaturnos.ui.abrirMapa
import com.example.agendaturnos.ui.abrirWhatsApp
import com.example.agendaturnos.ui.formatoFechaHora
import com.example.agendaturnos.ui.formatoPesos
import com.example.agendaturnos.ui.llamar
import kotlinx.coroutines.launch

/** "Agenda completa": todo el detalle de un turno. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnoDetalleScreen(
    repo: AgendaRepository,
    turnoId: Long,
    onVolver: () -> Unit,
    onAbrirCliente: (Long) -> Unit,
    onEditar: () -> Unit,
    onTerminar: () -> Unit,
    onVerDetalle: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val detalle by remember(turnoId) { repo.observarTurno(turnoId) }
        .collectAsStateWithLifecycle(initialValue = null)
    var confirmarCancelar by remember { mutableStateOf(false) }
    var motivo by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Turno") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (detalle?.turno?.estado == EstadoTurno.AGENDADO) {
                        IconButton(onClick = onEditar) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar o reprogramar")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val d = detalle
        if (d == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val t = d.turno
        val c = d.cliente
        val porCerrar = d.estaPorCerrar()
        val (estadoTexto, estadoColor) = when {
            porCerrar -> "Por cerrar" to MaterialTheme.colorScheme.error
            t.estado == EstadoTurno.AGENDADO -> "Agendado" to MaterialTheme.colorScheme.primary
            t.estado == EstadoTurno.TERMINADO -> "Terminado" to MaterialTheme.colorScheme.tertiary
            else -> "Cancelado" to MaterialTheme.colorScheme.error
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(estadoTexto, style = MaterialTheme.typography.labelLarge, color = estadoColor)
                    Text(t.fechaHora.format(formatoFechaHora), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${c.nombre} ›",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onAbrirCliente(c.id) },
                    )
                    Text(if (t.lugar == Lugar.DOMICILIO) "A domicilio" else "En consultorio")
                    t.direccion?.let { dir ->
                        Text(dir)
                        TextButton(onClick = { abrirMapa(context, dir) }) { Text("Ver en el mapa") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { llamar(context, c.telefono) }) { Text("Llamar") }
                        OutlinedButton(onClick = { abrirWhatsApp(context, c.telefono) }) { Text("WhatsApp") }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tratamientos", style = MaterialTheme.typography.titleMedium)
                    d.items.forEach { ic ->
                        Row {
                            Text(ic.tratamiento.nombre, modifier = Modifier.weight(1f))
                            Text(if (ic.item.precioCobrado > 0) formatoPesos(ic.item.precioCobrado) else "A determinar")
                        }
                    }
                    HorizontalDivider()
                    Row {
                        Text("Total", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(formatoPesos(d.total), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }

            c.preferencias?.let { Nota("Preferencias del cliente", it) }
            t.notas?.let { Nota("Notas del turno", it) }
            if (t.estado == EstadoTurno.CANCELADO) {
                Nota("Motivo de cancelación", t.motivoCancelacion ?: "Sin motivo")
            }

            if (porCerrar) {
                Button(onClick = onTerminar, modifier = Modifier.fillMaxWidth()) { Text("Terminar turno") }
            }
            if (t.estado == EstadoTurno.TERMINADO) {
                Button(onClick = onVerDetalle, modifier = Modifier.fillMaxWidth()) { Text("Ver detalle y fotos") }
            }
            if (t.estado == EstadoTurno.AGENDADO) {
                OutlinedButton(
                    onClick = { confirmarCancelar = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cancelar turno") }
            }
        }
    }

    if (confirmarCancelar) {
        AlertDialog(
            onDismissRequest = { confirmarCancelar = false },
            title = { Text("¿Cancelar el turno?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Queda en el historial como cancelado. Si solo cambia la fecha, usá el lápiz para reprogramar.")
                    OutlinedTextField(
                        value = motivo,
                        onValueChange = { motivo = it },
                        label = { Text("Motivo (opcional)") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmarCancelar = false
                    scope.launch { repo.cancelarTurno(turnoId, motivo) }
                }) { Text("Cancelar turno") }
            },
            dismissButton = { TextButton(onClick = { confirmarCancelar = false }) { Text("Volver") } },
        )
    }
}

@Composable
private fun Nota(titulo: String, texto: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(titulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(texto)
        }
    }
}
