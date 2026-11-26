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
    /** Posibles clientas (ya sin la profesional), de la que más escribió a la que menos. */
    val remitentes: List<Remitente>,
    /** Nombre que la persona dio en el chat ("Sara Palaian", "soy Laura"…). */
    val nombreEnTexto: String?,
    val telefonoEnTexto: String?,
    val fecha: LocalDate?,
    val hora: LocalTime?,
    val tratamientoIds: List<Long>,
    /** true = a domicilio, false = en consultorio, null = no se dijo. */
    val domicilio: Boolean?,
    /** Dirección o zona que dio la persona. */
    val direccion: String?,
    /** La clienta pidió cancelar o cambiar el turno al final de la charla. */
    val pidioReagendar: Boolean = false,
)

/**
 * Lee una conversación de WhatsApp (texto compartido o pegado) y deduce
 * cliente, fecha, hora, tratamientos y lugar con reglas simples en español rioplatense.
 * Funciona sin internet. No adivina: si algo no está claro, lo deja vacío.
 */
object LectorMensajes {

    private data class Mensaje(val quien: String?, val fecha: LocalDate?, val texto: String) {
        val norm: String = normalizar(texto)
    }

    // ---------- Encabezados de WhatsApp ----------
    // "[23/11, 06:56] Laura: Hola"  o  "[23/11/26, 06:56:10] Laura: Hola"  (copiar / compartir)
    private val encabezadoCorchetes = Regex(
        """^\[(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?,?\s+(\d{1,2}:\d{2})(?::\d{2})?(?:\s*[ap]\.?\s*m\.?)?]\s*([^:]{1,60}?):\s?(.*)$""",
        RegexOption.IGNORE_CASE,
    )
    // "23/11/26, 06:56 - Laura: Hola"  (exportar chat)
    private val encabezadoGuion = Regex(
        """^(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?,?\s+(\d{1,2}:\d{2})(?:\s*[ap]\.?\s*m\.?)?\s+-\s+([^:]{1,60}?):\s?(.*)$""",
        RegexOption.IGNORE_CASE,
    )
    /** Respuestas automáticas de WhatsApp Business: delatan cuál es la cuenta de la profesional. */
    private val respuestaAutomatica = Regex("""gracias por comunicarte|en este momento no podemos responder|gracias por tu mensaje""")

    private val telefonoRegex = Regex("""(?:\+?598[\s-]?)?\b0?9\d[\s-]?\d{3}[\s-]?\d{3}\b""")
    private val nombreDicho = Regex(
        """(?i)\b(?:me llamo|mi no[mn]bre es|mi no[mn]bre:?|soy)\s+([a-záéíóúñ]+(?:\s+[a-záéíóúñ]+)?)""",
    )
    /** Palabras que pueden aparecer donde se espera un nombre pero no lo son. */
    private val noSonNombres = setOf(
        "de", "del", "la", "el", "yo", "una", "un", "muy", "nueva", "nuevo", "cliente", "clienta", "gordita", "diabetica", "diabetico",
        "genial", "gracias", "dale", "ok", "oka", "okey", "perfecto", "perfecta", "si", "sii", "no", "bueno", "buena", "hola", "barbaro",
        "listo", "joya", "buenisimo", "excelente", "claro", "obvio", "encantada", "encantado", "igualmente", "saludos", "besos", "bien",
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
        "podolog" to listOf("podolog*", "pie", "pies", "unero*", "una encarnada", "unas encarnadas", "callo*", "dureza*", "hongo*", "honguito*", "pedicur*"),
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

    /** "jorge yeye" → "Jorge Yeye". */
    private fun capitalizar(t: String) = t.trim().split(Regex("\\s+"))
        .joinToString(" ") { p -> p.lowercase().replaceFirstChar { it.uppercase() } }

    /** Saca emojis y símbolos de los bordes del nombre del contacto ("🥰sol Nuñez" → "sol Nuñez"). */
    private fun limpiarNombreContacto(t: String) =
        t.trim().dropWhile { !it.isLetterOrDigit() && it != '+' }.dropLastWhile { !it.isLetterOrDigit() }

    fun analizar(
        texto: String,
        tratamientos: List<Pair<Long, String>>,
        ahora: LocalDateTime = LocalDateTime.now(),
        nombresPropios: Set<String> = emptySet(),
    ): ResultadoLectura {
        val hoy = ahora.toLocalDate()

        // 1) Separar mensajes: quién, qué día y qué dijo
        val mensajes = mutableListOf<Mensaje>()
        for (linea in texto.lines()) {
            val l = linea.trim()
            val m = encabezadoCorchetes.find(l) ?: encabezadoGuion.find(l)
            if (m != null) {
                val g = m.groupValues
                mensajes += Mensaje(limpiarNombreContacto(g[5]), fechaDeEncabezado(g[1], g[2], g[3], hoy), g[6])
            } else if (l.isNotEmpty()) {
                val ultimo = mensajes.lastOrNull()
                if (ultimo != null && ultimo.quien != null) {
                    mensajes[mensajes.lastIndex] = ultimo.copy(texto = ultimo.texto + "\n" + l)
                } else {
                    mensajes += Mensaje(null, null, l)
                }
            }
        }

        // 2) Quién es la profesional: la que manda respuestas automáticas, o un nombre ya conocido
        val propios = nombresPropios.map(::normalizar).toMutableSet()
        mensajes.filter { it.quien != null && respuestaAutomatica.containsMatchIn(it.norm) }
            .forEach { propios += normalizar(it.quien!!) }
        fun esPropio(m: Mensaje) = m.quien != null && normalizar(m.quien) in propios
        val utiles = mensajes.filterNot { esPropio(it) && respuestaAutomatica.containsMatchIn(it.norm) }

        val remitentes = utiles.mapNotNull { it.quien }.filter { normalizar(it) !in propios }
            .groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }
            .map { (quien, n) ->
                val tel = normalizarTelefono(quien)?.takeIf { quien.count(Char::isDigit) >= 8 }
                Remitente(nombre = if (tel != null) null else quien, telefono = tel, mensajes = n)
            }
        // Si se sabe quién es la profesional, se leen datos personales solo de lo que escribió la clienta
        val haySeparacion = propios.isNotEmpty() && utiles.any { esPropio(it) }
        fun esDeClienta(m: Mensaje) = !haySeparacion || !esPropio(m)

        // 3) Nombre
        val nombreEnTexto = buscarNombre(utiles, ::esPropio, ::esDeClienta)

        // 4) Teléfono y dirección (de lo que escribió la clienta)
        val textoClienta = utiles.filter(::esDeClienta).joinToString("\n") { it.texto }
        val telefonoEnTexto = telefonoRegex.findAll(textoClienta).mapNotNull { normalizarTelefono(it.value) }.lastOrNull()
        val direccion = buscarDireccion(utiles.filter(::esDeClienta).map { it.texto })

        // 5) Fecha y hora: gana la última mencionada (suele ser lo que se acordó)
        var mejorFecha: Pair<Long, LocalDate>? = null
        var mejorHora: Pair<Long, LocalTime>? = null
        var mensajeDeLaFecha = -1
        utiles.forEachIndexed { i, m ->
            val t = telefonoRegex.replace(m.norm) { " ".repeat(it.value.length) }
            buscarFecha(t, m.fecha ?: hoy)?.let { (pos, f) -> mejorFecha = (i * 100_000L + pos) to f; mensajeDeLaFecha = i }
            buscarHora(t)?.let { (pos, h) -> mejorHora = (i * 100_000L + pos) to h }
        }

        // 6) Tratamientos, lugar y si al final pidió cambiar el turno
        val todo = utiles.joinToString("\n") { it.norm }
        val ids = tratamientos.filter { (_, nombre) -> menciona(todo, nombre) }.map { it.first }
        // ¿Después de lo último acordado, la clienta pidió cancelar o cambiar?
        val despuesDeAcordar = utiles.drop(mensajeDeLaFecha + 1).filter(::esDeClienta).joinToString(" ") { it.norm }
        val reagendar = Regex("""\b(reagend\w*|re agend\w*|cancel\w*|suspend\w*|postergar|no voy a poder|no puedo ir|no voy a estar|cambiar (?:el|la) (?:turno|fecha|hora|dia))""")
            .containsMatchIn(despuesDeAcordar)

        return ResultadoLectura(
            remitentes = remitentes,
            nombreEnTexto = nombreEnTexto,
            telefonoEnTexto = telefonoEnTexto,
            fecha = mejorFecha?.second,
            hora = mejorHora?.second,
            tratamientoIds = ids,
            domicilio = buscarLugar(todo),
            direccion = direccion,
            pidioReagendar = reagendar,
        )
    }

    private fun fechaDeEncabezado(d: String, m: String, a: String, hoy: LocalDate): LocalDate? {
        val anio = a.toIntOrNull()?.let { if (it < 100) 2000 + it else it }
        val f = runCatching { LocalDate.of(anio ?: hoy.year, m.toInt(), d.toInt()) }.getOrNull() ?: return null
        // Sin año: un mensaje no puede ser del futuro, así que sería del año pasado
        return if (anio == null && f.isAfter(hoy.plusDays(1))) f.minusYears(1) else f
    }

    /**
     * El nombre, en orden de confianza:
     * 1. la respuesta a "¿cómo es tu nombre?"; 2. "me llamo / mi nombre es / soy X";
     * 3. "un gusto X" dicho por la profesional.
     */
    private fun buscarNombre(
        mensajes: List<Mensaje>,
        esPropio: (Mensaje) -> Boolean,
        esDeClienta: (Mensaje) -> Boolean,
    ): String? {
        // 1. Respuesta a la pregunta por el nombre
        mensajes.forEachIndexed { i, m ->
            val preguntaNombre = esPropio(m) && Regex("""\bno[mn]bre\b""").containsMatchIn(m.norm) &&
                (m.texto.contains('?') || Regex("""\b(como es|cual es|dime|decime|pasame|me das|me decis)\b""").containsMatchIn(m.norm))
            if (preguntaNombre) {
                val respuesta = mensajes.drop(i + 1).take(3).firstOrNull { esDeClienta(it) && !esPropio(it) }
                val t = respuesta?.texto?.trim()?.trimEnd('.', '!')
                if (t != null && t.length <= 40 && Regex("""^[\p{L}]+(?:\s+[\p{L}]+){0,3}$""").matches(t)) {
                    val palabras = normalizar(t).split(" ")
                    if (palabras.none { it in noSonNombres }) return capitalizar(t)
                }
            }
        }
        // 2. Lo dice ella
        mensajes.filter { esDeClienta(it) && !esPropio(it) || !mensajes.any(esPropio) }.forEach { m ->
            nombreDicho.findAll(m.texto).lastOrNull()?.let { r ->
                val n = r.groupValues[1]
                val primera = normalizar(n.split(" ").first())
                if (primera !in noSonNombres) {
                    val limpio = n.split(" ").takeWhile { normalizar(it) !in noSonNombres }.joinToString(" ")
                    if (limpio.isNotBlank()) return capitalizar(limpio)
                }
            }
        }
        // 3. "Un gusto Patricia" (lo dice la profesional)
        mensajes.filter(esPropio).forEach { m ->
            Regex("""(?i)\bun gusto\s+([a-záéíóúñ]+)""").find(m.texto)?.let { r ->
                val n = r.groupValues[1]
                if (normalizar(n) !in noSonNombres && normalizar(n) != "soy") return capitalizar(n)
            }
        }
        return null
    }

    /** "vivo en…", "mi dirección es…" pesan más que la zona ("soy de…", "estoy en…"). */
    private fun buscarDireccion(textos: List<String>): String? {
        val fuerte = Regex("""(?im)\b(?:vivo en|mi direcci[oó]n es|direcci[oó]n:?)\s+([^\n]{4,60}?)(?=[!?]|\.(?:\s|$)|$)""")
        val zona = Regex("""(?im)\b(?:soy de|somos de|estoy en|estoy frente (?:a|al)|somos)\s+([^\n]{3,50}?)(?=[!?,]|\.(?:\s|$)|$| cuanto| cuando)""")
        textos.asReversed().forEach { t -> fuerte.find(t)?.let { return it.groupValues[1].trim() } }
        textos.forEach { t ->
            zona.find(t)?.let { r ->
                val z = r.groupValues[1].trim()
                if (normalizar(z) !in setOf("sanatorio", "el sanatorio", "casa", "camino", "el trabajo")) return z
            }
        }
        return null
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
        val casa = Regex("""\b(a domicilio|domicilio|(?:a|en|por) (?:mi |tu )?casa|venis a|vengas a|venir a|ubicacion|me pases? la ubicacion)\b""")
        val consultorio = Regex("""\b(consultorio|voy yo|voy para (?:alla|ahi)|paso por (?:alla|ahi)|voy a tu)\b""")
        val ultCasa = casa.findAll(t).lastOrNull()?.range?.first ?: -1
        val ultCons = consultorio.findAll(t).lastOrNull()?.range?.first ?: -1
        return when {
            ultCasa < 0 && ultCons < 0 -> null
            else -> ultCasa > ultCons
        }
    }

    /** Devuelve (posición, fecha) de la última fecha mencionada en [t], relativa al día [base]. */
    private fun buscarFecha(t: String, base: LocalDate): Pair<Int, LocalDate>? {
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
                agregar(m.range, fechaSinAnio(m.groupValues[1].toInt(), i + 1, base))
            }
        }
        // "28/11", "28-11-2026"
        Regex("""\b(\d{1,2})[/-](\d{1,2})(?:[/-](\d{2,4}))?\b""").findAll(t).forEach { m ->
            val d = m.groupValues[1].toInt(); val mes = m.groupValues[2].toInt()
            val anio = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            agregar(m.range, if (anio != null) runCatching { LocalDate.of(anio, mes, d) }.getOrNull() else fechaSinAnio(d, mes, base))
        }
        // "miércoles 2", "lunes 30": el día de la semana con ese número
        Regex("""\b(lunes|martes|miercoles|jueves|viernes|sabado|domingo) (\d{1,2})\b(?!\s*(?:[:.]\d|hs?\b|horas))""").findAll(t).forEach { m ->
            val dia = dias.getValue(m.groupValues[1]); val n = m.groupValues[2].toInt()
            val f = (0L..62L).asSequence().map { base.plusDays(it) }.firstOrNull { it.dayOfMonth == n && it.dayOfWeek == dia }
            agregar(m.range, f ?: base.with(TemporalAdjusters.next(dia)))
        }
        // "pasado mañana", "mañana" (no "de la / en la / por la mañana"), "hoy"
        Regex("""\bpasado manana\b""").findAll(t).forEach { agregar(it.range, base.plusDays(2)) }
        Regex("""(?<!la )\bmanana\b""").findAll(t).forEach { agregar(it.range, base.plusDays(1)) }
        Regex("""\bhoy\b""").findAll(t).forEach { agregar(it.range, base) }
        // "el jueves", "para el miércoles"
        Regex("""\b(lunes|martes|miercoles|jueves|viernes|sabado|domingo)\b""").findAll(t).forEach { m ->
            agregar(m.range, base.with(TemporalAdjusters.next(dias.getValue(m.groupValues[1]))))
        }
        // "el 28" (día del mes)
        Regex("""\b(?:el|para el|dia) (\d{1,2})\b(?!\s*(?:[:.]\d|hs?\b|horas|y (?:media|cuarto)|de la))""").findAll(t).forEach { m ->
            val d = m.groupValues[1].toInt()
            if (d in 1..31) {
                val esteMes = runCatching { base.withDayOfMonth(d) }.getOrNull()
                agregar(m.range, if (esteMes != null && !esteMes.isBefore(base)) esteMes
                else runCatching { base.plusMonths(1).withDayOfMonth(d) }.getOrNull())
            }
        }
        return candidatas.maxByOrNull { it.first }
    }

    private fun fechaSinAnio(d: Int, mes: Int, base: LocalDate): LocalDate? {
        val f = runCatching { LocalDate.of(base.year, mes, d) }.getOrNull() ?: return null
        return if (f.isBefore(base.minusDays(1))) f.plusYears(1) else f
    }

    /** Devuelve (posición, hora) de la última hora mencionada en [t]. */
    private fun buscarHora(t: String): Pair<Int, LocalTime>? {
        val candidatas = mutableListOf<Pair<Int, LocalTime>>()
        val usado = BooleanArray(t.length)
        // "te lleva 1:30", "dura 45", "cuesta 1200": son duraciones o precios, no horas
        fun esDuracionOPrecio(inicio: Int, fin: Int): Boolean {
            val antes = t.substring(maxOf(0, inicio - 25), inicio)
            val despues = t.substring(fin, minOf(t.length, fin + 8))
            return Regex("""(lleva|dura|demora|tarda|mas o menos|aprox\w*|cuesta|sale|salen|costo|precio|vale|completa|\$|pesos)\s*\S*\s*$""").containsMatchIn(antes) ||
                Regex("""^\s*(pesos|\$|min)""").containsMatchIn(despues)
        }
        fun agregar(r: IntRange, h: Int, min: Int, sufijo: String?) {
            if (r.any { usado[it] } || esDuracionOPrecio(r.first, r.last + 1)) return
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
        // "a las 15:30", "a las 3 y media de la tarde", "hora 10", "las 14 horas"
        Regex("""\b(?:a las?|las?|hora|horario)\s+(\d{1,2})(?:[:.](\d{2})|\s+y\s+(media|cuarto))?$sufijo(?![\d/])""").findAll(t).forEach { m ->
            val min = m.groupValues[2].toIntOrNull() ?: when (m.groupValues[3]) { "media" -> 30; "cuarto" -> 15; else -> 0 }
            agregar(m.range, m.groupValues[1].toInt(), min, m.groupValues[4].ifEmpty { m.groupValues[5] }.ifEmpty { null })
        }
        // "16:30", "10.30 hs"
        Regex("""\b(\d{1,2})[:.](\d{2})$sufijo(?![\d/])""").findAll(t).forEach { m ->
            agregar(m.range, m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].ifEmpty { m.groupValues[4] }.ifEmpty { null })
        }
        // "1030" (sin separador): solo si parece una hora de trabajo con minutos redondos
        Regex("""\b([0-2]?\d)(00|15|30|45)\b(?!\s*(?:pesos|\$))""").findAll(t).forEach { m ->
            val h = m.groupValues[1].toInt()
            if (h in 7..21) agregar(m.range, h, m.groupValues[2].toInt(), "am")
        }
        // "17hs", "10 h de la mañana"
        Regex("""\b(\d{1,2})\s*(?:hs|h|horas)\b(?:\s*de la (manana|tarde|noche))?""").findAll(t).forEach { m ->
            agregar(m.range, m.groupValues[1].toInt(), 0, m.groupValues[2].ifEmpty { null })
        }
        Regex("""\b(\d{1,2})\s*(am|pm)\b""").findAll(t).forEach { m ->
            agregar(m.range, m.groupValues[1].toInt(), 0, m.groupValues[2])
        }
        Regex("""\bmediodia\b""").findAll(t).forEach { agregar(it.range, 12, 0, null) }
        return candidatas.maxByOrNull { it.first }
    }
}
