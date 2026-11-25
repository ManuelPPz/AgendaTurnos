package com.example.agendaturnos.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agendaturnos.data.AgendaRepository
import com.example.agendaturnos.data.model.TratamientoCatalogo
import com.example.agendaturnos.ui.formatoPesos
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogoScreen(repo: AgendaRepository, onVolver: () -> Unit) {
    val lista by remember { repo.todosLosTratamientos() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    // null = cerrado; id 0 = nuevo
    var editando by remember { mutableStateOf<TratamientoCatalogo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tratamientos") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editando = TratamientoCatalogo(nombre = "") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nuevo tratamiento") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            item {
                Text(
                    "El precio base es el que se propone al agendar; se puede cambiar en cada turno.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(lista, key = { it.id }) { t ->
                val apagado = MaterialTheme.colorScheme.onSurfaceVariant
                ListItem(
                    headlineContent = {
                        Text(t.nombre, color = if (t.activo) MaterialTheme.colorScheme.onSurface else apagado)
                    },
                    supportingContent = {
                        val partes = buildList {
                            add(if (t.precioBase > 0) formatoPesos(t.precioBase) else "Precio a determinar")
                            t.duracionMin?.let { add("$it min") }
                            if (!t.activo) add("Desactivado")
                        }
                        Text(partes.joinToString(" · "))
                    },
                    modifier = Modifier.clickable { editando = t },
                )
                HorizontalDivider()
            }
        }
    }

    editando?.let { t ->
        DialogoTratamiento(
            inicial = t,
            nombresExistentes = lista.filter { it.id != t.id }
                .map { AgendaRepository.normalizar(it.nombre) }.toSet(),
            onCancelar = { editando = null },
            onGuardar = { actualizado ->
                editando = null
                scope.launch { repo.guardarTratamiento(actualizado) }
            },
        )
    }
}

@Composable
private fun DialogoTratamiento(
    inicial: TratamientoCatalogo,
    nombresExistentes: Set<String>,
    onCancelar: () -> Unit,
    onGuardar: (TratamientoCatalogo) -> Unit,
) {
    val esNuevo = inicial.id == 0L
    var nombre by rememberSaveable { mutableStateOf(inicial.nombre) }
    var precio by rememberSaveable { mutableStateOf(if (inicial.precioBase > 0) inicial.precioBase.toString() else "") }
    var duracion by rememberSaveable { mutableStateOf(inicial.duracionMin?.toString() ?: "") }
    var activo by rememberSaveable { mutableStateOf(inicial.activo) }

    val nombreRepetido = AgendaRepository.normalizar(nombre) in nombresExistentes
    val valido = nombre.isNotBlank() && !nombreRepetido

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (esNuevo) "Nuevo tratamiento" else "Editar tratamiento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre") },
                    isError = nombreRepetido,
                    supportingText = { if (nombreRepetido) Text("Ya existe un tratamiento con ese nombre") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = precio,
                    onValueChange = { v -> precio = v.filter(Char::isDigit).take(7) },
                    label = { Text("Precio base (\$)") },
                    placeholder = { Text("A determinar") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = duracion,
                    onValueChange = { v -> duracion = v.filter(Char::isDigit).take(3) },
                    label = { Text("Duración estimada (min, opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!esNuevo) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Activo")
                            Text(
                                "Los desactivados no se ofrecen al agendar, pero siguen en el historial.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = activo, onCheckedChange = { activo = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valido,
                onClick = {
                    onGuardar(
                        inicial.copy(
                            nombre = nombre.trim(),
                            precioBase = precio.toLongOrNull() ?: 0L,
                            duracionMin = duracion.toIntOrNull(),
                            activo = activo,
                        )
                    )
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}
