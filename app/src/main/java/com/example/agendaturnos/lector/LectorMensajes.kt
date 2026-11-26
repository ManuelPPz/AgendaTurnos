package com.example.agendaturnos.lector

import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** Quién escribió en el chat (según el encabezado que agrega WhatsApp al copiar o compartir). */
data class Remitente(val nombre: String?, val telefono: String?, val mensajes: Int)

/** Lo que se pudo deducir de una conversación. Todo es opcional: lo que falta se completa a mano. */
data class ResultadoLectura(
    val remitentes: List<Remitente>,
    val nombreEnTexto: String?,
    val telefonoEnTexto: String?,
    val fecha: LocalDate?,
    val hora: LocalTime?,
    val tratamientoIds: List<Long>,
    /** true = a domicilio, false = en consultorio, null = no se dijo. */
    val domicilio: Boolean?,
    val direccion: String?,
)

/**
 * Lee una conversación de WhatsApp (texto compartido o pegado) y deduce
 * cliente, fecha, hora, tratamientos y lugar con reglas simples en español rioplatense.
 * Funciona sin internet. No adivina: si algo no está claro, lo deja vacío.
 */
object LectorMensajes {

    // ---------- Encabezados de WhatsApp ----------
    // "[26/11/26, 14:03] Laura: Hola"  (copiar / compartir)
    private val encabezadoCorchetes = Regex(
        """^\[(\d{1,2}/\d{1,2}/\d{2,4}),?\s+(\d{1,2}:\d{2})(?::\d{2})?(?:\s*[ap]\.?\s*m\.?)?]\s*([^:]{1,60}?):\s?(.*)$""",
        RegexOption.IGNORE_CASE,
    )
    // "26/11/26, 14:03 - Laura: Hola"  (exportar chat)
    private val encabezadoGuion = Regex(
        """^(\d{1,2}/\d{1,2}/\d{2,4}),?\s+(\d{1,2}:\d{2})(?:\s*[ap]\.?\s*m\.?)?\s+-\s+([^:]{1,60}?):\s?(.*)$""",
        RegexOption.IGNORE_CASE,
    )

    private val telefonoRegex = Regex("""(?:\+?598[\s-]?)?\b0?9\d[\s-]?\d{3}[\s-]?\d{3}\b""")
    private val nombreRegex = Regex(
        """\b(?:[Ss]oy|[Mm]e llamo|[Hh]abla|[Mm]i nombre es)\s+([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)?)""",
    )
    private val direccionRegex = Regex(
        """(?im)\b(?:vivo en|mi direcci[oó]n es|direcci[oó]n:?)\s+([^\n]{4,60}?)(?=[!?]|\.(?:\s|$)|$)""",
    )

    private val meses = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "setiembre|septiembre", "octubre", "noviembre", "diciembre",
    )
    private val dias = mapOf(
        "lunes" to DayOfWeek.MONDAY, "martes" to DayOfWeek.TUESDAY, "miercoles" to DayOfWeek.WEDNESDAY,
        "jueves" to DayOfWeek.THURSDAY, "viernes" to DayOfWeek.FRIDAY, "sabado" to DayOfWeek.SATURDAY,
        "domingo" to DayOfWeek.SUNDAY,
    )

    /**
     * Palabras que delatan cada tratamiento, además de su propio nombre.
     * Con "*" al final se acepta cualquier terminación ("esmalt*" = esmalte, esmaltado…).
     */
    private val sinonimos = mapOf(
        "podolog" to listOf("podolog*", "pie", "pies", "unero*", "una encarnada", "unas encarnadas", "callo*", "hongo*", "pedicur*"),
        "manicur" to listOf("manicur*", "manos", "esmalt*", "semipermanente*", "kapping", "unas de las manos", "esculpid*"),
        "auriculo" to listOf("auriculo*", "oreja*", "semilla*"),
    )

    fun normalizar(t: String): String =
        Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    /** "+598 99 123 456" / "099123456" → "099 123 456". */
    fun normalizarTelefono(t: String): String? {
        var d = t.filter(Char::isDigit)
        if (d.startsWith("598")) d = "0" + d.drop(3)
        if (d.length == 8 && d.startsWith("9")) d = "0$d"
        if (d.length != 9 || !d.startsWith("09")) return null
        return "${d.substring(0, 3)} ${d.substring(3, 6)} ${d.substring(6)}"
    }

    fun analizar(
        texto: String,
        tratamientos: List<Pair<Long, String>>,
        ahora: LocalDateTime = LocalDateTime.now(),
        nombresPropios: Set<String> = emptySet(),
    ): ResultadoLectura {
        // 1) Separar mensajes y remitentes
        val cuerpos = mutableListOf<String>()
        val conteo = linkedMapOf<String, Int>()
        for (linea in texto.lines()) {
            val m = encabezadoCorchetes.find(linea.trim()) ?: encabezadoGuion.find(linea.trim())
            if (m != null) {
                val quien = m.groupValues[3].trim().trimStart('‪', '‎').trimEnd('‬')
                conteo[quien] = (conteo[quien] ?: 0) + 1
                cuerpos += m.groupValues[4]
            } else if (linea.isNotBlank()) {
                cuerpos += linea
            }
        }
        val propios = nombresPropios.map(::normalizar).toSet()
        val remitentes = conteo.entries
            .filter { normalizar(it.key) !in propios }
            .sortedByDescending { it.value }
            .map { (quien, n) ->
                val tel = normalizarTelefono(quien)?.takeIf { quien.count(Char::isDigit) >= 8 }
                Remitente(nombre = if (tel != null) null else quien, telefono = tel, mensajes = n)
            }

        val original = cuerpos.joinToString("\n")
        val norm = normalizar(original)

        // 2) Datos de la persona escritos en el texto
        val nombreEnTexto = nombreRegex.findAll(original).lastOrNull()?.groupValues?.get(1)
        val telefonoEnTexto = telefonoRegex.findAll(original).mapNotNull { normalizarTelefono(it.value) }.lastOrNull()
        val direccion = direccionRegex.findAll(original).lastOrNull()?.groupValues?.get(1)?.trim()

        // 3) Fecha y hora: gana la última mencionada (suele ser lo que se acordó)
        val sinTelefonos = telefonoRegex.replace(norm) { " ".repeat(it.value.length) }
        val fecha = buscarFecha(sinTelefonos, ahora.toLocalDate())
        val hora = buscarHora(sinTelefonos)

        // 4) Tratamientos y lugar
        val ids = tratamientos.filter { (_, nombre) -> menciona(norm, nombre) }.map { it.first }
        val domicilio = buscarLugar(norm)

        return ResultadoLectura(remitentes, nombreEnTexto, telefonoEnTexto, fecha, hora, ids, domicilio, direccion)
    }

    private fun menciona(texto: String, nombreTratamiento: String): Boolean {
        val n = normalizar(nombreTratamiento).trim()
        // El propio nombre: completo, o su comienzo si es largo ("depilacion" → "depila*")
        val propio = if (n.length > 7 && !n.contains(' ')) n.take(6) + "*" else n
        val palabras = sinonimos.entries.firstOrNull { n.contains(it.key) }?.value.orEmpty() + propio
        return palabras.any { p ->
            val patron = if (p.endsWith("*")) Regex.escape(p.dropLast(1)) + "\\w*" else Regex.escape(p)
            Regex("""\b$patron\b""").containsMatchIn(texto)
        }
    }

    private fun buscarLugar(t: String): Boolean? {
        val casa = Regex("""\b(a domicilio|domicilio|(?:a|en|por) (?:mi |tu )?casa|venis a|vengas a|venir a)\b""")
        val consultorio = Regex("""\b(consultorio|voy yo|voy para (?:alla|ahi)|paso por (?:alla|ahi)|voy a tu)\b""")
        val ultCasa = casa.findAll(t).lastOrNull()?.range?.first ?: -1
        val ultCons = consultorio.findAll(t).lastOrNull()?.range?.first ?: -1
        return when {
            ultCasa < 0 && ultCons < 0 -> null
            else -> ultCasa > ultCons
        }
    }

    private fun buscarFecha(t: String, hoy: LocalDate): LocalDate? {
        val candidatas = mutableListOf<Pair<Int, LocalDate>>()
        val usado = BooleanArray(t.length)
        fun agregar(r: IntRange, f: LocalDate?) {
            if (f == null || r.any { usado[it] }) return
            r.forEach { usado[it] = true }
            candidatas += r.first to f
        }
        // "28 de noviembre"
        meses.forEachIndexed { i, mes ->
            Regex("""\b(\d{1,2}) de ($mes)\b""").findAll(t).forEach { m ->
                agregar(m.range, fechaSinAnio(m.groupValues[1].toInt(), i + 1, hoy))
            }
        }
        // "28/11", "28-11-2026"
        Regex("""\b(\d{1,2})[/-](\d{1,2})(?:[/-](\d{2,4}))?\b""").findAll(t).forEach { m ->
            val d = m.groupValues[1].toInt(); val mes = m.groupValues[2].toInt()
            val anio = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            agregar(m.range, if (anio != null) runCatching { LocalDate.of(anio, mes, d) }.getOrNull() else fechaSinAnio(d, mes, hoy))
        }
        // "pasado mañana", "mañana" (no "de la mañana"), "hoy"
        Regex("""\bpasado manana\b""").findAll(t).forEach { agregar(it.range, hoy.plusDays(2)) }
        Regex("""(?<!de la )(?<!por la )\bmanana\b""").findAll(t).forEach { agregar(it.range, hoy.plusDays(1)) }
        Regex("""\bhoy\b""").findAll(t).forEach { agregar(it.range, hoy) }
        // "el jueves", "el jueves que viene"
        Regex("""\b(lunes|martes|miercoles|jueves|viernes|sabado|domingo)\b""").findAll(t).forEach { m ->
            agregar(m.range, hoy.with(TemporalAdjusters.next(dias.getValue(m.groupValues[1]))))
        }
        // "el 28" (día del mes)
        Regex("""\b(?:el|para el|dia) (\d{1,2})\b(?!\s*(?:[:.]\d|hs?\b|horas|y (?:media|cuarto)|de la))""").findAll(t).forEach { m ->
            val d = m.groupValues[1].toInt()
            if (d in 1..31) {
                val esteMes = runCatching { hoy.withDayOfMonth(d) }.getOrNull()
                agregar(m.range, if (esteMes != null && !esteMes.isBefore(hoy)) esteMes
                else runCatching { hoy.plusMonths(1).withDayOfMonth(d) }.getOrNull())
            }
        }
        return candidatas.maxByOrNull { it.first }?.second
    }

    private fun fechaSinAnio(d: Int, mes: Int, hoy: LocalDate): LocalDate? {
        val f = runCatching { LocalDate.of(hoy.year, mes, d) }.getOrNull() ?: return null
        return if (f.isBefore(hoy.minusDays(1))) f.plusYears(1) else f
    }

    private fun buscarHora(t: String): LocalTime? {
        val candidatas = mutableListOf<Pair<Int, LocalTime>>()
        val usado = BooleanArray(t.length)
        fun agregar(r: IntRange, h: Int, min: Int, sufijo: String?) {
            if (r.any { usado[it] }) return
            var hh = h
            when {
                sufijo != null && (sufijo.startsWith("tarde") || sufijo.startsWith("noche") || sufijo == "pm") -> if (hh < 12) hh += 12
                sufijo != null && (sufijo.startsWith("man") || sufijo == "am") -> if (hh == 12) hh = 0
                hh in 1..7 -> hh += 12 // "a las 3" en una agenda de trabajo es de tarde
            }
            if (hh !in 0..23 || min !in 0..59) return
            r.forEach { usado[it] = true }
            candidatas += r.first to LocalTime.of(hh, min)
        }
        val sufijo = """(?:\s*(?:hs|h|horas)\.?)?(?:\s*(?:de la (manana|tarde|noche)|(am|pm)))?"""
        // "a las 15:30", "15.30 hs", "a las 3 y media de la tarde", "a las 10"
        Regex("""\b(?:a )?las? (\d{1,2})(?:[:.](\d{2})|\s+y\s+(media|cuarto))?$sufijo""").findAll(t).forEach { m ->
            val min = m.groupValues[2].toIntOrNull() ?: when (m.groupValues[3]) { "media" -> 30; "cuarto" -> 15; else -> 0 }
            agregar(m.range, m.groupValues[1].toInt(), min, m.groupValues[4].ifEmpty { m.groupValues[5] }.ifEmpty { null })
        }
        Regex("""\b(\d{1,2})[:.](\d{2})$sufijo""").findAll(t).forEach { m ->
            agregar(m.range, m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].ifEmpty { m.groupValues[4] }.ifEmpty { null })
        }
        Regex("""\b(\d{1,2})\s*(?:hs|h|horas)\b(?:\s*de la (manana|tarde|noche))?""").findAll(t).forEach { m ->
            agregar(m.range, m.groupValues[1].toInt(), 0, m.groupValues[2].ifEmpty { null })
        }
        Regex("""\b(\d{1,2})\s*(am|pm)\b""").findAll(t).forEach { m ->
            agregar(m.range, m.groupValues[1].toInt(), 0, m.groupValues[2])
        }
        Regex("""\bmediodia\b""").findAll(t).forEach { agregar(it.range, 12, 0, null) }
        return candidatas.maxByOrNull { it.first }?.second
    }
}
