package com.example.agendaturnos.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agendaturnos.data.AgendaRepository
import com.example.agendaturnos.data.model.Lugar
import com.example.agendaturnos.data.model.TipoFoto
import com.example.agendaturnos.ui.components.ImagenArchivo
import com.example.agendaturnos.ui.components.VisorFoto
import com.example.agendaturnos.ui.formatoFechaHora
import com.example.agendaturnos.ui.formatoPesos

/** Detalle de un tratamiento terminado, con fotos de antes y después. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleTratamientoScreen(
    repo: AgendaRepository,
    turnoId: Long,
    onVolver: () -> Unit,
    onAbrirCliente: (Long) -> Unit,
) {
    val detalle by remember(turnoId) { repo.observarTurno(turnoId) }
        .collectAsStateWithLifecycle(initialValue = null)
    var fotoAmpliada by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle del tratamiento") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(d.turno.fechaHora.format(formatoFechaHora), style = MaterialTheme.typography.headlineSmall)
            Text(
                "${d.cliente.nombre} ›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onAbrirCliente(d.cliente.id) },
            )
            Text(if (d.turno.lugar == Lugar.DOMICILIO) "A domicilio" else "En consultorio")

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.items.forEach { ic ->
                        Column {
                            Row {
                                Text(ic.tratamiento.nombre, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                Text(formatoPesos(ic.item.precioCobrado))
                            }
                            ic.item.descripcion?.let {
                                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    HorizontalDivider()
                    Row {
                        Text("Total cobrado", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text(formatoPesos(d.total), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }

            d.turno.notas?.let {
                Text("Notas: $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text("Fotos", style = MaterialTheme.typography.titleMedium)
            if (d.fotos.isEmpty()) {
                Text("Sin fotos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TipoFoto.entries.forEach { tipo ->
                        val titulo = if (tipo == TipoFoto.ANTES) "Antes" else "Después"
                        val foto = d.fotos.find { it.tipo == tipo }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(titulo, style = MaterialTheme.typography.labelLarge)
                            if (foto != null) {
                                ImagenArchivo(
                                    ruta = foto.rutaArchivo,
                                    descripcion = "Foto $titulo",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(3f / 4f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { fotoAmpliada = foto.rutaArchivo to titulo },
                                )
                            } else {
                                Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Text(
                    "Tocá una foto para verla en grande.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    fotoAmpliada?.let { (ruta, titulo) ->
        VisorFoto(ruta = ruta, descripcion = titulo, onCerrar = { fotoAmpliada = null })
    }
}
