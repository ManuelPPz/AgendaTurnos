package com.example.agendaturnos.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.agendaturnos.data.model.Lugar
import com.example.agendaturnos.data.model.TratamientoCatalogo
import com.example.agendaturnos.data.model.Turno
import com.example.agendaturnos.data.model.TurnoConDetalle
import com.example.agendaturnos.data.model.TurnoTratamiento
import com.example.agendaturnos.ui.formatoFechaCorta
import com.example.agendaturnos.ui.formatoFechaHora
import com.example.agendaturnos.ui.formatoHora
import com.example.agendaturnos.ui.formatoPesos
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Un tratamiento elegido para el turno. Precio vacío = a determinar. */
class ItemEditable(val tratamientoId: Long, val nombre: String, precioInicial: String) {
    var precio by mutableStateOf(precioInicial)
}

private fun textoPrecio(p: Long) = if (p > 0) p.toString() else ""

/** Estado del formulario de turno, compartido por agendar, nuevo cliente y editar. */
class TurnoEditorState(
    fecha: LocalDate? = null,
    hora: LocalTime? = null,
    lugar: Lugar = Lugar.CONSULTORIO,
    direccion: String = "",
    notas: String = "",
    itemsIniciales: List<ItemEditable> = emptyList(),
) {
    var fecha by mutableStateOf(fecha)
    var hora by mutableStateOf(hora)
    var lugar by mutableStateOf(lugar)
    var direccion by mutableStateOf(direccion)
    var notas by mutableStateOf(notas)
    val items: SnapshotStateList<ItemEditable> = mutableStateListOf<ItemEditable>().apply { addAll(itemsIniciales) }

    val total: Long get() = items.sumOf { it.precio.toLongOrNull() ?: 0L }

    val fechaHora: LocalDateTime?
        get() {
            val f = fecha
            val h = hora
            return if (f != null && h != null) f.atTime(h) else null
        }

    fun alternar(t: TratamientoCatalogo) {
        val i = items.indexOfFirst { it.tratamientoId == t.id }
        if (i >= 0) items.removeAt(i) else items.add(ItemEditable(t.id, t.nombre, textoPrecio(t.precioBase)))
    }

    /** "Repetir último": mismos tratamientos y lugar. Usa el precio actual del catálogo si tiene. */
    fun repetir(anterior: TurnoConDetalle, catalogo: List<TratamientoCatalogo>) {
        items.clear()
        anterior.items.forEach { ic ->
            val actual = catalogo.find { it.id == ic.item.tratamientoId }?.precioBase ?: 0L
            val precio = if (actual > 0) actual else ic.item.precioCobrado
            items.add(ItemEditable(ic.tratamiento.id, ic.tratamiento.nombre, textoPrecio(precio)))
        }
        lugar = anterior.turno.lugar
        anterior.turno.direccion?.let { direccion = it }
    }

    fun errores(): List<String> = buildList {
        if (fecha == null || hora == null) add("Elegí la fecha y la hora")
        if (items.isEmpty()) add("Elegí al menos un tratamiento")
        if (lugar == Lugar.DOMICILIO && direccion.isBlank()) add("Falta la dirección del domicilio")
    }

    fun aTurno(base: Turno?, clienteId: Long): Turno {
        val fh = requireNotNull(fechaHora)
        return (base ?: Turno(fechaHora = fh, lugar = lugar)).copy(
            clienteId = clienteId,
            fechaHora = fh,
            lugar = lugar,
            direccion = if (lugar == Lugar.DOMICILIO) direccion.trim().ifBlank { null } else null,
            notas = notas.trim().ifBlank { null },
        )
    }

    fun aItems(): List<TurnoTratamiento> = items.map {
        TurnoTratamiento(tratamientoId = it.tratamientoId, precioCobrado = it.precio.toLongOrNull() ?: 0L)
    }

    companion object {
        fun desde(d: TurnoConDetalle) = TurnoEditorState(
            fecha = d.turno.fechaHora.toLocalDate(),
            hora = d.turno.fechaHora.toLocalTime(),
            lugar = d.turno.lugar,
            direccion = d.turno.direccion.orEmpty(),
            notas = d.turno.notas.orEmpty(),
            itemsIniciales = d.items.map {
                ItemEditable(it.tratamiento.id, it.tratamiento.nombre, textoPrecio(it.item.precioCobrado))
            },
        )
    }
}

@Composable
fun EditorTurno(
    estado: TurnoEditorState,
    catalogo: List<TratamientoCatalogo>,
    direccionCliente: String?,
    mostrarErrores: Boolean,
) {
    var elegirFecha by remember { mutableStateOf(false) }
    var elegirHora by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Fecha y hora", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { elegirFecha = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.DateRange, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(estado.fecha?.format(formatoFechaCorta) ?: "Fecha")
            }
            OutlinedButton(onClick = { elegirHora = true }, modifier = Modifier.weight(1f)) {
                Text(estado.hora?.format(formatoHora) ?: "Hora")
            }
        }

        Text("Lugar", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Lugar.entries.forEach { l ->
                FilterChip(
                    selected = estado.lugar == l,
                    onClick = {
                        estado.lugar = l
                        if (l == Lugar.DOMICILIO && estado.direccion.isBlank()) {
                            estado.direccion = direccionCliente.orEmpty()
                        }
                    },
                    label = { Text(if (l == Lugar.DOMICILIO) "Domicilio" else "Consultorio") },
                )
            }
        }
        if (estado.lugar == Lugar.DOMICILIO) {
            OutlinedTextField(
                value = estado.direccion,
                onValueChange = { estado.direccion = it },
                label = { Text("Dirección") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text("Tratamientos", style = MaterialTheme.typography.titleSmall)
        catalogo.forEach { t ->
            FilaTratamiento(t.nombre, estado.items.find { it.tratamientoId == t.id }) { estado.alternar(t) }
        }
        // Tratamientos que ya estaban en el turno pero hoy están desactivados en el catálogo.
        estado.items.filter { i -> catalogo.none { it.id == i.tratamientoId } }.forEach { i ->
            FilaTratamiento(i.nombre, i) { estado.items.remove(i) }
        }
        if (catalogo.isEmpty()) {
            Text(
                "No hay tratamientos activos. Agregalos en Menú → Tratamientos.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("Total: ${formatoPesos(estado.total)}", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = estado.notas,
            onValueChange = { estado.notas = it },
            label = { Text("Notas del turno (opcional)") },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        if (mostrarErrores) {
            estado.errores().forEach {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (elegirFecha) {
        DialogoFecha(
            inicial = estado.fecha,
            escribirFecha = false,
            onElegir = { estado.fecha = it },
            onCerrar = { elegirFecha = false },
        )
    }
    if (elegirHora) {
        DialogoHora(
            inicial = estado.hora,
            onElegir = { estado.hora = it },
            onCerrar = { elegirHora = false },
        )
    }
}

@Composable
private fun FilaTratamiento(nombre: String, item: ItemEditable?, onAlternar: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onAlternar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item != null, onCheckedChange = { onAlternar() })
        Text(nombre, modifier = Modifier.weight(1f))
        if (item != null) {
            OutlinedTextField(
                value = item.precio,
                onValueChange = { v -> item.precio = v.filter(Char::isDigit).take(7) },
                prefix = { Text("$ ") },
                placeholder = { Text("A det.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(130.dp),
            )
        }
    }
}

/** Aviso cuando ya hay otro turno a la misma hora. No bloquea. */
@Composable
fun DialogoChoque(fechaHora: LocalDateTime, onConfirmar: () -> Unit, onCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Horario ocupado") },
        text = { Text("Ya hay un turno el ${fechaHora.format(formatoFechaHora)}. ¿Agendar igual?") },
        confirmButton = { TextButton(onClick = onConfirmar) { Text("Agendar igual") } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cambiar horario") } },
    )
}
