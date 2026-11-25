package com.example.agendaturnos.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agendaturnos.data.AgendaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun BuscarClientesScreen(
    repo: AgendaRepository,
    onVolver: () -> Unit,
    onAbrirCliente: (Long) -> Unit,
    onNuevoCliente: () -> Unit,
) {
    var texto by rememberSaveable { mutableStateOf("") }
    val textoFlow = remember { MutableStateFlow(texto) }
    LaunchedEffect(texto) { textoFlow.value = texto }
    // Se actualiza mientras escribís, con una pequeña espera para no buscar en cada tecla.
    val resultados by remember {
        textoFlow.debounce(150).flatMapLatest { repo.buscarClientes(it) }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clientes") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNuevoCliente,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nuevo cliente") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Buscar por nombre o teléfono") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (texto.isNotEmpty()) {
                        IconButton(onClick = { texto = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            if (resultados.isEmpty()) {
                Text(
                    if (texto.isBlank()) "Todavía no hay clientes cargados." else "No se encontró \"$texto\".",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(resultados, key = { it.id }) { c ->
                        ListItem(
                            headlineContent = { Text(c.nombre) },
                            supportingContent = { Text(c.telefono) },
                            modifier = Modifier.clickable { onAbrirCliente(c.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
