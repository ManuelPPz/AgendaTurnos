package com.example.agendaturnos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agendaturnos.data.AgendaRepository
import com.example.agendaturnos.data.model.TurnoConDetalle
import com.example.agendaturnos.ui.components.DialogoChoque
import com.example.agendaturnos.ui.components.EditorTurno
import com.example.agendaturnos.ui.components.TurnoEditorState
import kotlinx.coroutines.launch

/** Editar o reprogramar un turno agendado. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditarTurnoScreen(
    repo: AgendaRepository,
    turnoId: Long,
    onVolver: () -> Unit,
    onGuardado: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val catalogo by remember { repo.tratamientosActivos() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var detalle by remember { mutableStateOf<TurnoConDetalle?>(null) }
    var estado by remember { mutableStateOf<TurnoEditorState?>(null) }
    var intento by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var mostrarChoque by remember { mutableStateOf(false) }

    LaunchedEffect(turnoId) {
        val d = repo.obtenerTurno(turnoId)
        detalle = d
        if (d != null) estado = TurnoEditorState.desde(d)
    }

    fun guardar() {
        val d = detalle ?: return
        val e = estado ?: return
        guardando = true
        scope.launch {
            repo.actualizarTurno(e.aTurno(d.turno, d.turno.clienteId), e.aItems())
            guardando = false
            onGuardado()
        }
    }

    fun confirmar() {
        val e = estado ?: return
        intento = true
        if (e.errores().isNotEmpty() || guardando) return
        scope.launch {
            val ocupado = repo.hayTurnoEnMismoHorario(requireNotNull(e.fechaHora), excluirId = turnoId)
            if (ocupado) mostrarChoque = true else guardar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar turno") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        val d = detalle
        val e = estado
        if (d == null || e == null) {
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
            EditorTurno(
                estado = e,
                catalogo = catalogo,
                direccionCliente = d.cliente.direccion,
                mostrarErrores = intento,
            )
            Button(
                onClick = { confirmar() },
                enabled = !guardando,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar cambios") }
        }
    }

    val fh = estado?.fechaHora
    if (mostrarChoque && fh != null) {
        DialogoChoque(
            fechaHora = fh,
            onConfirmar = { mostrarChoque = false; guardar() },
            onCancelar = { mostrarChoque = false },
        )
    }
}
