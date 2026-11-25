package com.example.agendaturnos.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agendaturnos.data.AgendaRepository
import com.example.agendaturnos.data.model.EstadoTurno
import com.example.agendaturnos.data.model.TurnoConDetalle
import com.example.agendaturnos.ui.formatoFechaLarga
import com.example.agendaturnos.ui.formatoPesos

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorialScreen(
    repo: AgendaRepository,
    clienteId: Long,
    onVolver: () -> Unit,
    onAbrir: (TurnoConDetalle) -> Unit,
) {
    var incluirCancelados by rememberSaveable { mutableStateOf(false) }
    val cliente by remember(clienteId) { repo.observarCliente(clienteId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val lista by remember(clienteId, incluirCancelados) { repo.historial(clienteId, incluirCancelados) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val terminados = lista.filter { it.turno.estado == EstadoTurno.TERMINADO }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(cliente?.nombre ?: "", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${terminados.size} ${if (terminados.size == 1) "visita" else "visitas"} · " +
                            "Total cobrado ${formatoPesos(terminados.sumOf { it.total })}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilterChip(
                        selected = incluirCancelados,
                        onClick = { incluirCancelados = !incluirCancelados },
                        label = { Text("Mostrar cancelados") },
                    )
                }
            }
            if (lista.isEmpty()) {
                item {
                    Text(
                        "Todavía no hay tratamientos terminados.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(lista, key = { it.turno.id }) { t ->
                val cancelado = t.turno.estado == EstadoTurno.CANCELADO
                Card(Modifier.fillMaxWidth().clickable { onAbrir(t) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.turno.fechaHora.format(formatoFechaLarga), style = MaterialTheme.typography.labelLarge)
                            Text(t.resumenTratamientos, style = MaterialTheme.typography.titleMedium)
                            if (cancelado) {
                                Text("Cancelado", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            } else if (t.fotos.isNotEmpty()) {
                                Text(
                                    "Con fotos",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (!cancelado) {
                            Text(formatoPesos(t.total), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}
