package com.example.agendaturnos.data.db

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Fechas guardadas como texto con formato fijo,
 * así se ordenan y comparan bien en SQL.
 */
class Converters {
    private val formatoFechaHora = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    @TypeConverter
    fun fechaHoraATexto(valor: LocalDateTime?): String? = valor?.format(formatoFechaHora)

    @TypeConverter
    fun textoAFechaHora(valor: String?): LocalDateTime? =
        valor?.let { LocalDateTime.parse(it, formatoFechaHora) }

    @TypeConverter
    fun fechaATexto(valor: LocalDate?): String? = valor?.toString()

    @TypeConverter
    fun textoAFecha(valor: String?): LocalDate? = valor?.let(LocalDate::parse)
}
