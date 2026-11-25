package com.example.agendaturnos.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.agendaturnos.data.AgendaRepository
import com.example.agendaturnos.data.AlmacenFotos
import com.example.agendaturnos.data.model.TipoFoto
import com.example.agendaturnos.drive.RespaldoDriveWorker
import com.example.agendaturnos.data.model.TurnoConDetalle
import com.example.agendaturnos.data.model.TurnoTratamiento
import com.example.agendaturnos.ui.components.ImagenArchivo
import com.example.agendaturnos.ui.formatoFechaHora
import com.example.agendaturnos.ui.formatoPesos
import kotlinx.coroutines.launch
import java.io.File

/** Datos editables de cada tratamiento al cerrar. */
private class ItemCierre(val tratamientoId: Long, val nombre: String, precio: String) {
    var precio by mutableStateOf(precio)
    var descripcion by mutableStateOf("")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CerrarTurnoScreen(
    repo: AgendaRepository,
    turnoId: Long,
    onVolver: () -> Unit,
    onListo: () -> Unit,
    onReprogramar: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var detalle by remember { mutableStateOf<TurnoConDetalle?>(null) }
    val items = remember { mutableStateListOf<ItemCierre>() }
    var terminado by rememberSaveable { mutableStateOf(true) }
    var motivo by rememberSaveable { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }

    // Fotos elegidas, todavía sin confirmar
    val fotos = remember { mutableStateMapOf<TipoFoto, File>() }
    var tipoPendiente by rememberSaveable { mutableStateOf<TipoFoto?>(null) }
    var rutaCamara by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(turnoId) {
        val d = repo.obtenerTurno(turnoId)
        detalle = d
        if (d != null && items.isEmpty()) {
            d.items.forEach {
                items.add(
                    ItemCierre(
                        it.tratamiento.id,
                        it.tratamiento.nombre,
                        if (it.item.precioCobrado > 0) it.item.precioCobrado.toString() else "",
                    )
                )
            }
        }
    }

    fun recibirFoto(origen: Uri, borrarOrigen: File?) {
        val tipo = tipoPendiente ?: return
        scope.launch {
            val comprimida = AlmacenFotos.comprimir(context, origen)
            borrarOrigen?.delete()
            if (comprimida != null) {
                fotos[tipo]?.delete()
                fotos[tipo] = comprimida
            }
        }
    }

    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val archivo = rutaCamara?.let { File(it) }
        if (ok && archivo != null) recibirFoto(Uri.fromFile(archivo), archivo) else archivo?.delete()
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) recibirFoto(uri, null)
    }

    fun abrirCamara(tipo: TipoFoto) {
        tipoPendiente = tipo
        val archivo = AlmacenFotos.nuevoArchivoCamara(context)
        rutaCamara = archivo.absolutePath
        camara.launch(AlmacenFotos.uriPara(context, archivo))
    }

    fun abrirGaleria(tipo: TipoFoto) {
        tipoPendiente = tipo
        galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun confirmar() {
        if (guardando) return
        guardando = true
        scope.launch {
            if (terminado) {
                val sello = System.currentTimeMillis()
                val rutas = fotos.mapValues { (tipo, archivo) ->
                    AlmacenFotos.confirmar(context, archivo, "turno_${turnoId}_${tipo.name.lowercase()}_$sello")
                }
                repo.cerrarTurnoTerminado(
                    turnoId,
                    items.map {
                        TurnoTratamiento(
                            tratamientoId = it.tratamientoId,
                            precioCobrado = it.precio.toLongOrNull() ?: 0L,
                            descripcion = it.descripcion.trim().ifBlank { null },
                        )
                    },
                    rutas,
                )
            } else {
                repo.cancelarTurno(turnoId, motivo)
            }
            guardando = false
            RespaldoDriveWorker.respaldarAhora(context)
            onListo()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cerrar turno") },
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
            Text(d.cliente.nombre, style = MaterialTheme.typography.titleLarge)
            Text(d.turno.fechaHora.format(formatoFechaHora), color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("¿Cómo terminó?", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = terminado, onClick = { terminado = true }, label = { Text("Terminado") })
                FilterChip(selected = !terminado, onClick = { terminado = false }, label = { Text("Cancelado") })
            }

            if (terminado) {
                Text("Tratamientos realizados", style = MaterialTheme.typography.titleSmall)
                items.forEach { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.nombre, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                OutlinedTextField(
                                    value = item.precio,
                                    onValueChange = { v -> item.precio = v.filter(Char::isDigit).take(7) },
                                    prefix = { Text("$ ") },
                                    placeholder = { Text("0") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(130.dp),
                                )
                            }
                            OutlinedTextField(
                                value = item.descripcion,
                                onValueChange = { item.descripcion = it },
                                label = { Text("Qué se hizo (opcional)") },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                Text(
                    "Total cobrado: ${formatoPesos(items.sumOf { it.precio.toLongOrNull() ?: 0L })}",
                    style = MaterialTheme.typography.titleMedium,
                )

                Text("Fotos (opcionales)", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TipoFoto.entries.forEach { tipo ->
                        RanuraFoto(
                            titulo = if (tipo == TipoFoto.ANTES) "Antes" else "Después",
                            archivo = fotos[tipo],
                            onCamara = { abrirCamara(tipo) },
                            onGaleria = { abrirGaleria(tipo) },
                            onQuitar = { fotos.remove(tipo)?.delete() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = motivo,
                    onValueChange = { motivo = it },
                    label = { Text("Motivo (opcional)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onReprogramar) { Text("¿Solo cambia la fecha? Reprogramar en su lugar") }
            }

            Button(
                onClick = { confirmar() },
                enabled = !guardando,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (terminado) "Confirmar como terminado" else "Confirmar cancelación") }
        }
    }
}

@Composable
private fun RanuraFoto(
    titulo: String,
    archivo: File?,
    onCamara: () -> Unit,
    onGaleria: () -> Unit,
    onQuitar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(titulo, style = MaterialTheme.typography.labelLarge)
        val forma = RoundedCornerShape(12.dp)
        if (archivo != null) {
            ImagenArchivo(
                ruta = archivo.absolutePath,
                descripcion = "Foto $titulo",
                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(forma),
            )
            TextButton(onClick = onQuitar) { Text("Quitar") }
        } else {
            OutlinedButton(onClick = onCamara, modifier = Modifier.fillMaxWidth()) { Text("Cámara") }
            OutlinedButton(onClick = onGaleria, modifier = Modifier.fillMaxWidth()) { Text("Galería") }
        }
    }
}
