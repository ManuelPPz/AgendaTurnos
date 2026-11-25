package com.example.agendaturnos.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.pm.ApplicationInfo
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agendaturnos.data.AgendaRepository
import com.example.agendaturnos.data.model.TurnoConDetalle
import com.example.agendaturnos.ui.navigation.Rutas
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formatoFecha =
    DateTimeFormatter.ofPattern("EEE d 'de' MMM, HH:mm", Locale.forLanguageTag("es-UY"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InicioScreen(
    repo: AgendaRepository,
    onAbrirTurno: (Long) -> Unit,
    onCerrarTurno: (Long) -> Unit,
    onNavegar: (String) -> Unit,
) {
    val ahora = remember { LocalDateTime.now() }
    val porCerrar by remember { repo.turnosPorCerrar(ahora) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val proximos by remember { repo.proximosTurnos(ahora) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var menuAbierto by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Solo en la versión de desarrollo (la que se instala desde Android Studio).
    val esDesarrollo = remember { (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agenda") },
                actions = {
                    Box {
                        IconButton(onClick = { menuAbierto = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menú")
                        }
                        DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
                            listOf(
                                "Buscar cliente" to Rutas.BUSCAR,
                                "Nuevo cliente + turno" to Rutas.NUEVO_CLIENTE,
                                "Agendar cliente existente" to Rutas.agendar(),
                                "Tratamientos" to Rutas.CATALOGO,
                            ).forEach { (texto, ruta) ->
                                DropdownMenuItem(
                                    text = { Text(texto) },
                                    onClick = { menuAbierto = false; onNavegar(ruta) },
                                )
                            }
                            if (esDesarrollo) {
                                DropdownMenuItem(
                                    text = { Text("Cargar datos de prueba") },
                                    onClick = {
                                        menuAbierto = false
                                        scope.launch {
                                            val cargados = repo.cargarDatosDePrueba()
                                            Toast.makeText(
                                                context,
                                                if (cargados) "Datos de prueba cargados" else "Los datos de prueba ya estaban cargados",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavegar(Rutas.agendar()) }) {
                Icon(Icons.Default.Add, contentDescription = "Agendar turno")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (porCerrar.isNotEmpty()) {
                item { TituloSeccion("Turnos por cerrar") }
                items(porCerrar, key = { "c${it.turno.id}" }) { t ->
                    TarjetaTurno(t, porCerrar = true, onClick = { onAbrirTurno(t.turno.id) }, onTerminar = { onCerrarTurno(t.turno.id) })
                }
            }
            item { TituloSeccion("Próximos turnos") }
            if (proximos.isEmpty()) {
                item {
                    Text(
                        "No hay turnos agendados. Tocá + para agendar uno.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(proximos, key = { "p${it.turno.id}" }) { t ->
                    TarjetaTurno(t, porCerrar = false, onClick = { onAbrirTurno(t.turno.id) }, onTerminar = {})
                }
            }
        }
    }
}

@Composable
private fun TituloSeccion(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun TarjetaTurno(
    t: TurnoConDetalle,
    porCerrar: Boolean,
    onClick: () -> Unit,
    onTerminar: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = if (porCerrar) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(t.turno.fechaHora.format(formatoFecha), style = MaterialTheme.typography.labelLarge)
                Text(t.cliente.nombre, style = MaterialTheme.typography.titleMedium)
                if (t.items.isNotEmpty()) {
                    Text(
                        t.resumenTratamientos,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (porCerrar) {
                Button(onClick = onTerminar) { Text("Terminar") }
            }
        }
    }
}
