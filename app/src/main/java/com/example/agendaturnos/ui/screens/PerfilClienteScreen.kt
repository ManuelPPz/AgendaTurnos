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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agendaturnos.data.AgendaRepository
import com.example.agendaturnos.ui.abrirMapa
import com.example.agendaturnos.ui.abrirWhatsApp
import com.example.agendaturnos.ui.edad
import com.example.agendaturnos.ui.etiqueta
import com.example.agendaturnos.ui.formatoFechaHora
import com.example.agendaturnos.ui.formatoFechaLarga
import com.example.agendaturnos.ui.llamar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilClienteScreen(
    repo: AgendaRepository,
    clienteId: Long,
    onVolver: () -> Unit,
    onEditar: () -> Unit,
    onHistorial: () -> Unit,
    onAgendar: () -> Unit,
    onAbrirTurno: (Long) -> Unit,
) {
    val context = LocalContext.current
    val cliente by remember(clienteId) { repo.observarCliente(clienteId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val proximo by remember(clienteId) { repo.proximoTurnoDeCliente(clienteId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val ultimo by remember(clienteId) { repo.ultimoTerminadoDeCliente(clienteId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val visitas by remember(clienteId) { repo.cantidadVisitas(clienteId) }
        .collectAsStateWithLifecycle(initialValue = 0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(cliente?.nombre ?: "") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onEditar) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar datos")
                    }
                },
            )
        },
    ) { padding ->
        val c = cliente
        if (c == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!c.activo) {
                Text("Cliente archivado", color = MaterialTheme.colorScheme.error)
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Dato("Teléfono", c.telefono)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { llamar(context, c.telefono) }) { Text("Llamar") }
                        OutlinedButton(onClick = { abrirWhatsApp(context, c.telefono) }) { Text("WhatsApp") }
                    }
                    Dato("Dirección de atención", c.direccion ?: "—")
                    c.direccion?.let { dir ->
                        TextButton(onClick = { abrirMapa(context, dir) }) { Text("Ver en el mapa") }
                    }
                    Dato("Género", c.genero?.etiqueta() ?: "—")
                    Dato(
                        "Cumpleaños",
                        c.fechaNacimiento?.let { "${it.format(formatoFechaLarga)} (${edad(it)} años)" } ?: "—",
                    )
                    Dato("Preferencias", c.preferencias ?: "—")
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Dato("Visitas", visitas.toString())
                    Dato(
                        "Última visita",
                        ultimo?.let { "${it.turno.fechaHora.format(formatoFechaLarga)} · ${it.resumenTratamientos}" }
                            ?: "Sin visitas todavía",
                    )
                    val p = proximo
                    if (p == null) {
                        Dato("Próximo turno", "Sin turno agendado")
                    } else {
                        Column(Modifier.clickable { onAbrirTurno(p.turno.id) }) {
                            Dato("Próximo turno", "${p.turno.fechaHora.format(formatoFechaHora)} · ${p.resumenTratamientos}")
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAgendar, modifier = Modifier.weight(1f)) { Text("Agendar turno") }
                OutlinedButton(onClick = onHistorial, modifier = Modifier.weight(1f)) { Text("Historial") }
            }
        }
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String) {
    Column {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyLarge)
    }
}
