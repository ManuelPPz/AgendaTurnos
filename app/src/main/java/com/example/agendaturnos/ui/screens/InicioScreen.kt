package com.example.agendaturnos.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agendaturnos.AgendaApp
import com.example.agendaturnos.data.AgendaRepository
import com.example.agendaturnos.data.model.ClienteRecontacto
import com.example.agendaturnos.data.model.TurnoConDetalle
import com.example.agendaturnos.ui.abrirWhatsApp
import com.example.agendaturnos.ui.formatoFechaHora
import com.example.agendaturnos.ui.navigation.Rutas
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InicioScreen(
    repo: AgendaRepository,
    onAbrirTurno: (Long) -> Unit,
    onCerrarTurno: (Long) -> Unit,
    onNavegar: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val programador = remember { (context.applicationContext as AgendaApp).programador }

    val ahora = remember { LocalDateTime.now() }
    val porCerrar by remember { repo.turnosPorCerrar(ahora) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val proximos by remember { repo.proximosTurnos(ahora) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val recontactar by remember { repo.clientesParaRecontactar() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var menuAbierto by remember { mutableStateOf(false) }
    // Solo en la versión de desarrollo (la que se instala desde Android Studio).
    val esDesarrollo = remember { (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 }

    // ---- Permisos para los avisos ----
    val pedirNotificaciones = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var exactasOk by remember { mutableStateOf(programador.puedeExactas()) }
    LifecycleResumeEffect(Unit) {
        val antes = exactasOk
        exactasOk = programador.puedeExactas()
        if (!antes && exactasOk) scope.launch { repo.reprogramarTodosLosAvisos() }
        onPauseOrDispose { }
    }

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
                                            repo.reprogramarTodosLosAvisos()
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
            if (!exactasOk) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Para que los avisos lleguen a la hora justa, permití las alarmas exactas.")
                            TextButton(onClick = {
                                if (Build.VERSION.SDK_INT >= 31) {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            Uri.parse("package:${context.packageName}"),
                                        )
                                    )
                                }
                            }) { Text("Permitir") }
                        }
                    }
                }
            }

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

            if (recontactar.isNotEmpty()) {
                item { TituloSeccion("Para recontactar") }
                items(recontactar, key = { "r${it.cliente.id}" }) { r ->
                    TarjetaRecontacto(
                        r = r,
                        onAbrir = { onNavegar(Rutas.cliente(r.cliente.id)) },
                        onWhatsApp = {
                            abrirWhatsApp(context, r.cliente.telefono, mensajeRecontacto(r.cliente.nombre))
                            scope.launch { repo.marcarRecontactoEnviado(r.cliente.id) }
                        },
                        onAgendar = { onNavegar(Rutas.agendar(r.cliente.id)) },
                        onPosponer = { scope.launch { repo.posponerRecontacto(r.cliente.id) } },
                        onDescartar = { scope.launch { repo.descartarRecontacto(r) } },
                    )
                }
            }
        }
    }
}

private fun mensajeRecontacto(nombre: String): String {
    val primerNombre = nombre.trim().substringBefore(' ')
    return "¡Hola $primerNombre! ¿Cómo estás? Ya pasaron unas semanas desde tu última sesión. " +
        "¿Querés que coordinemos un turno?"
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
                Text(t.turno.fechaHora.format(formatoFechaHora), style = MaterialTheme.typography.labelLarge)
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

@Composable
private fun TarjetaRecontacto(
    r: ClienteRecontacto,
    onAbrir: () -> Unit,
    onWhatsApp: () -> Unit,
    onAgendar: () -> Unit,
    onPosponer: () -> Unit,
    onDescartar: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onAbrir),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Text(r.cliente.nombre, style = MaterialTheme.typography.titleMedium)
            Text(
                "Última visita hace ${r.diasDesdeUltimaVisita()} días",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row {
                TextButton(onClick = onWhatsApp) { Text("WhatsApp") }
                TextButton(onClick = onAgendar) { Text("Agendar") }
            }
            Row {
                TextButton(onClick = onPosponer) { Text("Posponer 7 días") }
                TextButton(onClick = onDescartar) { Text("Descartar") }
            }
        }
    }
}
