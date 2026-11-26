package com.example.agendaturnos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agendaturnos.data.AgendaRepository
import com.example.agendaturnos.data.model.Cliente
import com.example.agendaturnos.lector.Borrador
import com.example.agendaturnos.lector.DatosBorrador
import com.example.agendaturnos.lector.LectorMensajes
import com.example.agendaturnos.lector.MensajeCompartido
import com.example.agendaturnos.lector.NombresPropios
import com.example.agendaturnos.ui.formatoDia
import com.example.agendaturnos.ui.formatoHora

/**
 * Lee una conversación (compartida desde WhatsApp o pegada), muestra lo que entendió
 * y precarga el formulario de agendar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DesdeMensajeScreen(
    repo: AgendaRepository,
    onVolver: () -> Unit,
    onAgendarExistente: (clienteId: Long) -> Unit,
    onNuevoCliente: () -> Unit,
) {
    val context = LocalContext.current
    var texto by rememberSaveable { mutableStateOf(MensajeCompartido.tomar().orEmpty()) }
    var pegando by rememberSaveable { mutableStateOf(texto.isBlank()) }
    var textoPegado by rememberSaveable { mutableStateOf("") }
    var verTodo by rememberSaveable { mutableStateOf(false) }

    val catalogo by remember { repo.tratamientosActivos() }.collectAsStateWithLifecycle(initialValue = null)
    val clientes by remember { repo.buscarClientes("") }.collectAsStateWithLifecycle(initialValue = emptyList())
    val propios = remember { NombresPropios.obtener(context) }

    val resultado = remember(texto, catalogo) {
        val cat = catalogo
        if (texto.isBlank() || cat == null) null
        else LectorMensajes.analizar(texto, cat.map { it.id to it.nombre }, nombresPropios = propios)
    }

    var elegido by rememberSaveable(texto) { mutableStateOf(0) }
    var nombre by rememberSaveable(texto) { mutableStateOf("") }
    var telefono by rememberSaveable(texto) { mutableStateOf("") }
    val tratamientosElegidos = remember(resultado) {
        mutableStateListOf<Long>().apply { resultado?.tratamientoIds?.let(::addAll) }
    }
    // Al leer (o al cambiar de remitente) se completan nombre y teléfono
    LaunchedEffect(resultado, elegido) {
        val r = resultado ?: return@LaunchedEffect
        val rem = r.remitentes.getOrNull(elegido)
        nombre = rem?.nombre ?: r.nombreEnTexto ?: ""
        telefono = rem?.telefono ?: r.telefonoEnTexto ?: ""
    }

    val existente = remember(nombre, telefono, clientes) { buscarExistente(clientes, nombre, telefono) }
    var esOtraPersona by remember(existente?.id) { mutableStateOf(false) }
    val clienteElegido = existente?.takeIf { !esOtraPersona }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agendar desde mensaje") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (pegando) {
                Text(
                    "Pegá acá la conversación. También podés, desde WhatsApp, mantener apretados los mensajes " +
                        "→ Compartir → Agenda.",
                )
                OutlinedTextField(
                    value = textoPegado,
                    onValueChange = { textoPegado = it },
                    label = { Text("Conversación") },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { texto = textoPegado; pegando = false },
                    enabled = textoPegado.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Leer") }
                return@Column
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        texto,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (verTodo) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow {
                        TextButton(onClick = { verTodo = !verTodo }) { Text(if (verTodo) "Ver menos" else "Ver todo") }
                        TextButton(onClick = { textoPegado = texto; pegando = true }) { Text("Editar mensaje") }
                    }
                }
            }

            val r = resultado ?: return@Column

            // ----- Cliente -----
            Text("Cliente", style = MaterialTheme.typography.titleMedium)
            if (r.remitentes.size > 1) {
                Text("¿Quién es la clienta en este chat?", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    r.remitentes.forEachIndexed { i, rem ->
                        FilterChip(
                            selected = elegido == i,
                            onClick = { elegido = i },
                            label = { Text(rem.nombre ?: rem.telefono ?: "?") },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = telefono,
                onValueChange = { telefono = it },
                label = { Text("Teléfono") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            if (clienteElegido != null) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Ya está cargada: ${clienteElegido.nombre} (${clienteElegido.telefono})")
                        TextButton(onClick = { esOtraPersona = true }) { Text("Es otra persona") }
                    }
                }
            } else {
                Text(
                    "Se va a cargar como clienta nueva.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ----- Turno -----
            Text("Turno", style = MaterialTheme.typography.titleMedium)
            Dato("Día", r.fecha?.format(formatoDia)?.replaceFirstChar { it.uppercase() } ?: "No se encontró — lo elegís en el próximo paso")
            Dato("Hora", r.hora?.format(formatoHora) ?: "No se encontró — la elegís en el próximo paso")
            Dato(
                "Lugar",
                when (r.domicilio) {
                    true -> "A domicilio" + (r.direccion?.let { " ($it)" } ?: "")
                    false -> "En consultorio"
                    null -> "No se dijo (queda en consultorio)"
                },
            )
            Text("Tratamientos", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                catalogo.orEmpty().forEach { t ->
                    FilterChip(
                        selected = t.id in tratamientosElegidos,
                        onClick = { if (!tratamientosElegidos.remove(t.id)) tratamientosElegidos.add(t.id) },
                        label = { Text(t.nombre) },
                    )
                }
            }
            Text(
                "En el siguiente paso revisás y podés cambiar todo antes de confirmar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    // Los otros participantes del chat son la profesional: se recuerdan para la próxima.
                    if (r.remitentes.size > 1) {
                        NombresPropios.agregar(
                            context,
                            r.remitentes.filterIndexed { i, _ -> i != elegido }.mapNotNull { it.nombre },
                        )
                    }
                    val direccionCliente = r.direccion ?: clienteElegido?.direccion ?: ""
                    Borrador.poner(
                        DatosBorrador(
                            nombre = nombre.trim(),
                            telefono = telefono.trim(),
                            direccionCliente = r.direccion.orEmpty(),
                            fecha = r.fecha,
                            hora = r.hora,
                            domicilio = r.domicilio,
                            direccionTurno = if (r.domicilio == true) direccionCliente else "",
                            tratamientos = catalogo.orEmpty().filter { it.id in tratamientosElegidos },
                        )
                    )
                    if (clienteElegido != null) onAgendarExistente(clienteElegido.id) else onNuevoCliente()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Continuar") }
        }
    }
}

/** Busca si la persona ya está cargada: primero por teléfono, después por nombre. */
private fun buscarExistente(clientes: List<Cliente>, nombre: String, telefono: String): Cliente? {
    val digitos = telefono.filter(Char::isDigit).takeLast(8)
    if (digitos.length == 8) {
        clientes.firstOrNull { it.telefono.filter(Char::isDigit).takeLast(8) == digitos }?.let { return it }
    }
    val n = AgendaRepository.normalizar(nombre)
    if (n.length < 3) return null
    clientes.firstOrNull { it.nombreBusqueda == n }?.let { return it }
    val parecidos = clientes.filter { it.nombreBusqueda.startsWith("$n ") }
    return parecidos.singleOrNull()
}

@Composable
private fun Dato(etiqueta: String, valor: String) {
    Column {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyLarge)
    }
}
