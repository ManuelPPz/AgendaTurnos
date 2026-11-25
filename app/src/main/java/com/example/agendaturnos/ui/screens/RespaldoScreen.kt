package com.example.agendaturnos.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.example.agendaturnos.ui.formatoFechaHora
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RespaldoScreen(onVolver: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as AgendaApp
    val scope = rememberCoroutineScope()
    var ultimo by remember { mutableStateOf(Respaldo.ultimoRespaldo(context)) }
    var trabajando by remember { mutableStateOf(false) }
    var archivoARestaurar by remember { mutableStateOf<Uri?>(null) }

    fun avisar(texto: String) = Toast.makeText(context, texto, Toast.LENGTH_LONG).show()

    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            trabajando = true
            scope.launch {
                runCatching { Respaldo.exportar(context, app.database, uri) }
                    .onSuccess {
                        ultimo = Respaldo.ultimoRespaldo(context)
                        avisar("Respaldo guardado")
                    }
                    .onFailure { avisar("No se pudo guardar el respaldo") }
                trabajando = false
            }
        }
    }
    val elegirArchivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        archivoARestaurar = uri
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "El respaldo guarda clientes, turnos, historial y fotos en un archivo .zip. " +
                    "Guardalo en Google Drive o mandátelo por mail para no perder nada si cambiás o perdés el celular.",
            )
            Text(
                ultimo?.let { "Último respaldo: ${it.format(formatoFechaHora)}" } ?: "Todavía no hiciste ningún respaldo.",
                style = MaterialTheme.typography.titleSmall,
            )
            if (trabajando) LinearProgressIndicator(Modifier.fillMaxWidth())

            Button(
                onClick = { exportar.launch("agenda-respaldo-${LocalDate.now()}.zip") },
                enabled = !trabajando,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Crear respaldo") }

            OutlinedButton(
                onClick = { elegirArchivo.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                enabled = !trabajando,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Restaurar desde un respaldo") }
        }
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
                    trabajando = true
                    scope.launch {
                        val ok = runCatching { Respaldo.restaurar(context, app.database, uri) }.getOrDefault(false)
                        trabajando = false
                        if (ok) {
                            // Reinicia la app para abrir la base restaurada.
                            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { i ->
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                context.startActivity(i)
                            }
                            Runtime.getRuntime().exit(0)
                        } else {
                            avisar("Ese archivo no es un respaldo válido. No se cambió nada.")
                        }
                    }
                }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { archivoARestaurar = null }) { Text("Cancelar") } },
        )
    }
}
