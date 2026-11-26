package com.example.agendaturnos.ui.screens

import android.Manifest
import android.content.Intent
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
import androidx.compose.material3.AlertDialog
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
import com.example.agendaturnos.data.Respaldo
import com.example.agendaturnos.data.model.ClienteRecontacto
import com.example.agendaturnos.data.model.TurnoConDetalle
import com.example.agendaturnos.drive.DriveRespaldo
import com.example.agendaturnos.ui.abrirWhatsApp
import com.example.agendaturnos.ui.formatoFechaHora
import com.example.agendaturnos.ui.navigation.Rutas
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

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
    // Si quedaron cargados los clientes de ejemplo, el menú ofrece borrarlos.
    var hayDatosDePrueba by remember { mutableStateOf(false) }
    var confirmarBorrarPrueba by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hayDatosDePrueba = repo.hayDatosDePrueba() }

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
    var ultimoRespaldo by remember { mutableStateOf(Respaldo.ultimoRespaldo(context)) }
    var cuentaDrive by remember { mutableStateOf(DriveRespaldo.cuenta(context)) }
    LifecycleResumeEffect(Unit) {
        ultimoRespaldo = Respaldo.ultimoRespaldo(context)
        cuentaDrive = DriveRespaldo.cuenta(context)
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
                                "Agendar desde mensaje" to Rutas.DESDE_MENSAJE,
                                "Tratamientos" to Rutas.CATALOGO,
                                "Respaldo" to Rutas.RESPALDO,
                            ).forEach { (texto, ruta) ->
                                DropdownMenuItem(
                                    text = { Text(texto) },
                                    onClick = { menuAbierto = false; onNavegar(ruta) },
                                )
                            }
                            if (hayDatosDePrueba) {
                                DropdownMenuItem(
                                    text = { Text("Borrar datos de prueba") },
                                    onClick = {
                                        menuAbierto = false
                                        confirmarBorrarPrueba = true
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

            val diasSinRespaldo = ultimoRespaldo?.let { ChronoUnit.DAYS.between(it.toLocalDate(), LocalDate.now()) }
            val hayDatos = porCerrar.isNotEmpty() || proximos.isNotEmpty() || recontactar.isNotEmpty()
            if (!hayDatos && cuentaDrive == null) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("¿Cambiaste de celular? Recuperá tus datos desde Google Drive.")
                            TextButton(onClick = { onNavegar(Rutas.RESPALDO) }) { Text("Conectar con Google Drive") }
                        }
                    }
                }
            }
            if (hayDatos && cuentaDrive == null && (diasSinRespaldo == null || diasSinRespaldo >= 14)) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                if (diasSinRespaldo == null) "Todavía no hiciste ningún respaldo de tus datos."
                                else "Hace $diasSinRespaldo días que no hacés un respaldo.",
                            )
                            TextButton(onClick = { onNavegar(Rutas.RESPALDO) }) { Text("Hacer respaldo") }
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
                proximos.groupBy { grupoDeFecha(it.turno.fechaHora.toLocalDate()) }.forEach { (grupo, lista) ->
                    item(key = "g$grupo") {
                        Text(
                            grupo,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(lista, key = { "p${it.turno.id}" }) { t ->
                        TarjetaTurno(t, porCerrar = false, onClick = { onAbrirTurno(t.turno.id) }, onTerminar = {})
                    }
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

    if (confirmarBorrarPrueba) {
        DialogoBorrarPrueba(
            onConfirmar = {
                confirmarBorrarPrueba = false
                scope.launch {
                    val borrados = repo.borrarDatosDePrueba()
                    hayDatosDePrueba = false
                    Toast.makeText(context, "Se borraron $borrados clientes de prueba", Toast.LENGTH_SHORT).show()
                }
            },
            onCancelar = { confirmarBorrarPrueba = false },
        )
    }
}

/** Agrupa los próximos turnos: Hoy, Mañana, Esta semana o Más adelante. */
private fun grupoDeFecha(fecha: LocalDate, hoy: LocalDate = LocalDate.now()): String = when {
    fecha == hoy -> "Hoy"
    fecha == hoy.plusDays(1) -> "Mañana"
    !fecha.isAfter(hoy.plusDays(7)) -> "Esta semana"
    else -> "Más adelante"
}

@Composable
private fun DialogoBorrarPrueba(onConfirmar: () -> Unit, onCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("¿Borrar los datos de prueba?") },
        text = {
            Text(
                "Se borran los 8 clientes de ejemplo (María González, Ana Rodríguez, etc.) con sus turnos. " +
                    "Tus clientes y turnos reales no se tocan.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirmar) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
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
