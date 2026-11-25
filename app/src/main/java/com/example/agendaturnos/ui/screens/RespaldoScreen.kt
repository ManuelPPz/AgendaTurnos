package com.example.agendaturnos.ui.screens

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.agendaturnos.AgendaApp
import com.example.agendaturnos.data.Respaldo
import com.example.agendaturnos.drive.DriveRespaldo
import com.example.agendaturnos.drive.RespaldoDriveWorker
import com.example.agendaturnos.ui.formatoFechaHora
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RespaldoScreen(onVolver: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as AgendaApp
    val scope = rememberCoroutineScope()

    var ultimoLocal by remember { mutableStateOf(Respaldo.ultimoRespaldo(context)) }
    var cuenta by remember { mutableStateOf(DriveRespaldo.cuenta(context)) }
    var ultimaSubida by remember { mutableStateOf(DriveRespaldo.ultimaSubida(context)) }
    var trabajando by remember { mutableStateOf<String?>(null) } // texto de lo que se está haciendo
    var archivoARestaurar by remember { mutableStateOf<Uri?>(null) }
    var respaldoDrive by remember { mutableStateOf<Pair<String, DriveRespaldo.ArchivoDrive>?>(null) } // token + archivo
    var confirmarDesconectar by remember { mutableStateOf(false) }
    // Qué hacer con el token cuando Google devuelve el permiso
    var siguientePaso by remember { mutableStateOf<(suspend (String) -> Unit)?>(null) }

    fun avisar(texto: String) = Toast.makeText(context, texto, Toast.LENGTH_LONG).show()

    fun refrescar() {
        ultimoLocal = Respaldo.ultimoRespaldo(context)
        cuenta = DriveRespaldo.cuenta(context)
        ultimaSubida = DriveRespaldo.ultimaSubida(context)
    }

    fun tarea(texto: String, bloque: suspend () -> Unit) {
        trabajando = texto
        scope.launch {
            try {
                bloque()
            } catch (e: Exception) {
                avisar("No se pudo completar: ${e.message ?: "error de conexión"}")
            } finally {
                trabajando = null
                refrescar()
            }
        }
    }

    // ---------- Respaldo manual en archivo ----------
    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) tarea("Guardando respaldo…") {
            Respaldo.exportar(context, app.database, uri)
            avisar("Respaldo guardado")
        }
    }
    val elegirArchivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        archivoARestaurar = uri
    }

    // ---------- Google Drive ----------
    val pantallaGoogle = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val paso = siguientePaso
        siguientePaso = null
        if (r.resultCode != Activity.RESULT_OK || paso == null) {
            trabajando = null
            return@rememberLauncherForActivityResult
        }
        val token = runCatching {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(r.data).accessToken
        }.getOrNull()
        if (token == null) {
            trabajando = null
            avisar("No se pudo conectar con Google")
        } else {
            tarea(trabajando ?: "Conectando…") { paso(token) }
        }
    }

    /** Consigue un token de Drive (mostrando la pantalla de Google si hace falta) y sigue con [paso]. */
    fun conToken(texto: String, email: String?, paso: suspend (String) -> Unit) {
        trabajando = texto
        scope.launch {
            try {
                val resultado = DriveRespaldo.autorizar(context, email)
                val pendiente = resultado.pendingIntent
                val token = resultado.accessToken
                if (resultado.hasResolution() && pendiente != null) {
                    siguientePaso = paso
                    pantallaGoogle.launch(IntentSenderRequest.Builder(pendiente.intentSender).build())
                } else if (token != null) {
                    paso(token)
                    trabajando = null
                    refrescar()
                } else {
                    trabajando = null
                    avisar("No se pudo conectar con Google")
                }
            } catch (e: Exception) {
                trabajando = null
                avisar("No se pudo conectar con Google: ${e.message ?: ""}")
            }
        }
    }

    /** Después de conectar: recordar la cuenta, programar el respaldo diario y ofrecer restaurar si hay algo en Drive. */
    val alConectar: suspend (String) -> Unit = { token ->
        DriveRespaldo.guardarCuenta(context, DriveRespaldo.emailDeCuenta(token))
        RespaldoDriveWorker.programarDiario(context)
        val existentes = DriveRespaldo.listar(token)
        if (existentes.isNotEmpty()) {
            respaldoDrive = token to existentes.first()
        } else {
            DriveRespaldo.subir(context, app.database, token)
            avisar("Conectado. Primer respaldo subido a Google Drive.")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Respaldo") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            trabajando?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            // ----- Google Drive -----
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Google Drive", style = MaterialTheme.typography.titleMedium)
                    val c = cuenta
                    if (c == null) {
                        Text(
                            "Conectá tu cuenta de Google y la app sube un respaldo automático todos los días. " +
                                "Si cambiás de celular, instalás la app, conectás la misma cuenta y recuperás todo.",
                        )
                        Button(
                            onClick = { conToken("Conectando con Google…", null, alConectar) },
                            enabled = trabajando == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Conectar con Google Drive") }
                    } else {
                        Text(if (c.isBlank()) "Cuenta de Google conectada" else "Conectado como $c")
                        Text(
                            ultimaSubida?.let { "Último respaldo en Drive: ${it.format(formatoFechaHora)}" }
                                ?: "Todavía no se subió ningún respaldo.",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "Se respalda solo una vez por día y después de cerrar cada turno (con internet).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                conToken("Subiendo respaldo a Drive…", c) { token ->
                                    DriveRespaldo.subir(context, app.database, token)
                                    avisar("Respaldo subido a Google Drive")
                                }
                            },
                            enabled = trabajando == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Respaldar ahora") }
                        OutlinedButton(
                            onClick = {
                                conToken("Buscando respaldos en Drive…", c) { token ->
                                    val ultimo = DriveRespaldo.listar(token).firstOrNull()
                                    if (ultimo == null) avisar("No hay respaldos en Drive para esta cuenta")
                                    else respaldoDrive = token to ultimo
                                }
                            },
                            enabled = trabajando == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Restaurar desde Drive") }
                        TextButton(onClick = { confirmarDesconectar = true }, enabled = trabajando == null) {
                            Text("Desconectar cuenta")
                        }
                    }
                }
            }

            HorizontalDivider()

            // ----- Archivo manual -----
            Text("Respaldo en archivo", style = MaterialTheme.typography.titleMedium)
            Text(
                "También podés guardar el respaldo como archivo .zip donde quieras (Drive, mail, computadora).",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                ultimoLocal?.let { "Último respaldo (cualquiera): ${it.format(formatoFechaHora)}" }
                    ?: "Todavía no hiciste ningún respaldo.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = { exportar.launch("agenda-respaldo-${LocalDate.now()}.zip") },
                enabled = trabajando == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar respaldo en archivo") }
            OutlinedButton(
                onClick = { elegirArchivo.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                enabled = trabajando == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Restaurar desde archivo") }
        }
    }

    respaldoDrive?.let { (token, archivo) ->
        AlertDialog(
            onDismissRequest = { respaldoDrive = null },
            title = { Text("Respaldo encontrado en Drive") },
            text = {
                Text(
                    "Hay un respaldo del ${archivo.fecha.format(formatoFechaHora)} " +
                        "(${archivo.bytes / 1024} KB).\n\n¿Querés restaurarlo? Se reemplazan TODOS los datos de este celular " +
                        "por los del respaldo, y la app se reinicia.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    respaldoDrive = null
                    tarea("Descargando y restaurando…") {
                        if (DriveRespaldo.restaurar(context, app.database, token, archivo)) {
                            Respaldo.reiniciarApp(context)
                        } else {
                            avisar("El respaldo de Drive no es válido. No se cambió nada.")
                        }
                    }
                }) { Text("Restaurar") }
            },
            dismissButton = {
                TextButton(onClick = { respaldoDrive = null }) { Text("Ahora no") }
            },
        )
    }

    archivoARestaurar?.let { uri ->
        AlertDialog(
            onDismissRequest = { archivoARestaurar = null },
            title = { Text("¿Restaurar este respaldo?") },
            text = {
                Text(
                    "Se van a reemplazar TODOS los datos actuales (clientes, turnos y fotos) por los del respaldo. " +
                        "No se puede deshacer. La app se va a reiniciar.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    archivoARestaurar = null
                    tarea("Restaurando…") {
                        if (Respaldo.restaurar(context, app.database, uri)) {
                            Respaldo.reiniciarApp(context)
                        } else {
                            avisar("Ese archivo no es un respaldo válido. No se cambió nada.")
                        }
                    }
                }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { archivoARestaurar = null }) { Text("Cancelar") } },
        )
    }

    if (confirmarDesconectar) {
        AlertDialog(
            onDismissRequest = { confirmarDesconectar = false },
            title = { Text("¿Desconectar Google Drive?") },
            text = { Text("Se dejan de subir respaldos automáticos. Los respaldos que ya están en Drive no se borran.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarDesconectar = false
                    RespaldoDriveWorker.cancelar(context)
                    DriveRespaldo.desconectar(context)
                    refrescar()
                }) { Text("Desconectar") }
            },
            dismissButton = { TextButton(onClick = { confirmarDesconectar = false }) { Text("Cancelar") } },
        )
    }
}
