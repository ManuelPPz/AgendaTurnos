package com.example.agendaturnos.lector

import android.content.Context
import com.example.agendaturnos.data.model.Lugar
import com.example.agendaturnos.data.model.TratamientoCatalogo
import com.example.agendaturnos.ui.components.ItemEditable
import com.example.agendaturnos.ui.components.TurnoEditorState
import java.time.LocalDate
import java.time.LocalTime

/** Texto recibido con "Compartir" (WhatsApp → Compartir → Agenda), a la espera de ser leído. */
object MensajeCompartido {
    @Volatile private var texto: String? = null
    fun poner(t: String) { texto = t }
    fun tomar(): String? = texto.also { texto = null }
}

/** Datos ya revisados que precargan el formulario de agendar o de cliente nuevo. */
data class DatosBorrador(
    val nombre: String,
    val telefono: String,
    val direccionCliente: String,
    val fecha: LocalDate?,
    val hora: LocalTime?,
    val domicilio: Boolean?,
    val direccionTurno: String,
    val tratamientos: List<TratamientoCatalogo>,
) {
    fun aEstadoTurno() = TurnoEditorState(
        fecha = fecha,
        hora = hora,
        lugar = if (domicilio == true) Lugar.DOMICILIO else Lugar.CONSULTORIO,
        direccion = direccionTurno,
        itemsIniciales = tratamientos.map {
            ItemEditable(it.id, it.nombre, if (it.precioBase > 0) it.precioBase.toString() else "")
        },
    )
}

object Borrador {
    @Volatile private var datos: DatosBorrador? = null
    fun poner(d: DatosBorrador) { datos = d }
    /** Se usa una sola vez: el formulario que lo toma lo consume. */
    fun tomar(): DatosBorrador? = datos.also { datos = null }
}

/**
 * Nombres con los que aparece la profesional en los chats (los que no son clientas).
 * Se aprenden solos: cuando en un chat hay dos personas y se elige a la clienta, la otra se recuerda.
 */
object NombresPropios {
    private const val PREFS = "lector"
    private const val CLAVE = "propios"

    fun obtener(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(CLAVE, emptySet()).orEmpty()

    fun agregar(ctx: Context, nombres: Collection<String>) {
        if (nombres.isEmpty()) return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(CLAVE, obtener(ctx) + nombres).apply()
    }
}
