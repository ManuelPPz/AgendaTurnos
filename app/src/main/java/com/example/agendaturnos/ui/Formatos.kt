package com.example.agendaturnos.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.agendaturnos.data.model.Genero
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.Locale

val LOCALE_UY: Locale = Locale.forLanguageTag("es-UY")

val formatoFechaHora: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d 'de' MMM, HH:mm", LOCALE_UY)
val formatoFechaLarga: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", LOCALE_UY)
val formatoDia: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", LOCALE_UY)
val formatoHora: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", LOCALE_UY)
val formatoFechaCorta: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE_UY)

fun edad(nacimiento: LocalDate, hoy: LocalDate = LocalDate.now()): Int =
    Period.between(nacimiento, hoy).years

fun Genero.etiqueta(): String = when (this) {
    Genero.MASCULINO -> "Masculino"
    Genero.FEMENINO -> "Femenino"
}

fun formatoPesos(monto: Long): String = "$ " + NumberFormat.getIntegerInstance(LOCALE_UY).format(monto)

/**
 * Convierte un teléfono uruguayo al formato de WhatsApp.
 * "099 123 456" -> "59899123456". Si ya empieza con 598 se deja igual.
 */
fun numeroWhatsApp(telefono: String): String {
    val digitos = telefono.filter(Char::isDigit)
    return when {
        digitos.startsWith("598") -> digitos
        digitos.startsWith("0") -> "598" + digitos.drop(1)
        else -> "598$digitos"
    }
}

private fun abrir(context: Context, intent: Intent, siFalla: String) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, siFalla, Toast.LENGTH_SHORT).show()
    }
}

fun abrirWhatsApp(context: Context, telefono: String, mensaje: String = "") {
    val texto = if (mensaje.isBlank()) "" else "?text=" + Uri.encode(mensaje)
    abrir(
        context,
        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${numeroWhatsApp(telefono)}$texto")),
        "No se pudo abrir WhatsApp",
    )
}

fun llamar(context: Context, telefono: String) {
    abrir(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$telefono")), "No se pudo abrir el teléfono")
}

fun abrirMapa(context: Context, direccion: String) {
    abrir(
        context,
        Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(direccion))),
        "No hay app de mapas",
    )
}
