package com.example.agendaturnos

import com.example.agendaturnos.lector.LectorMensajes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Pruebas del lector de mensajes de WhatsApp. "Hoy" es el jueves 26/11/2026 a las 16:00. */
class LectorMensajesTest {
    private val catalogo = listOf(1L to "Podología", 2L to "Auriculoterapia", 3L to "Manicura")
    private val ahora = LocalDateTime.of(2026, 11, 26, 16, 0)
    private fun leer(texto: String, propios: Set<String> = emptySet()) =
        LectorMensajes.analizar(texto, catalogo, ahora, propios)

    @Test fun mensajeSimple() {
        val r = leer("Hola! soy Laura, tenés lugar mañana a las 15 para manicura?")
        assertEquals("Laura", r.nombreEnTexto)
        assertEquals(LocalDate.of(2026, 11, 27), r.fecha)
        assertEquals(LocalTime.of(15, 0), r.hora)
        assertEquals(listOf(3L), r.tratamientoIds)
    }

    @Test fun chatCopiadoGanaLoUltimoAcordado() {
        val r = leer(
            """
            [26/11/26, 14:03] Laura Pérez: Hola! tenés lugar el martes?
            [26/11/26, 14:05] Marta: Hola Laura! el martes a las 10 o a las 16:30
            [26/11/26, 14:06] Laura Pérez: a las 16:30 dale, para los pies que tengo un uñero
            """.trimIndent()
        )
        assertEquals("Laura Pérez", r.remitentes.first().nombre)
        assertEquals(LocalDate.of(2026, 12, 1), r.fecha)
        assertEquals(LocalTime.of(16, 30), r.hora)
        assertEquals(listOf(1L), r.tratamientoIds)
    }

    @Test fun chatExportadoConNumeroYDomicilio() {
        val r = leer(
            """
            26/11/26, 14:03 - +598 99 123 456: Buenas, quería pedir hora para podología a domicilio
            26/11/26, 14:04 - +598 99 123 456: vivo en Av. Brasil 2345, apto 4
            26/11/26, 14:10 - Marta: Perfecto, el 3 de diciembre a las 3 de la tarde te viene bien?
            """.trimIndent()
        )
        assertEquals("099 123 456", r.remitentes.first().telefono)
        assertEquals(LocalDate.of(2026, 12, 3), r.fecha)
        assertEquals(LocalTime.of(15, 0), r.hora)
        assertEquals(true, r.domicilio)
        assertEquals("Av. Brasil 2345, apto 4", r.direccion)
    }

    @Test fun excluyeNombresPropios() {
        val r = leer(
            """
            [26/11/26, 14:05] Marta: Hola! te queda bien el viernes 28/11 a las 9 y media?
            [26/11/26, 14:06] Sofi: siii perfecto, auriculoterapia y manos
            """.trimIndent(),
            propios = setOf("Marta"),
        )
        assertEquals(listOf("Sofi"), r.remitentes.map { it.nombre })
        assertEquals(LocalTime.of(9, 30), r.hora)
        assertEquals(listOf(2L, 3L), r.tratamientoIds)
    }

    @Test fun deLaMananaNoEsManana() {
        val r = leer("el lunes a las 10 de la mañana en el consultorio")
        assertEquals(LocalDate.of(2026, 11, 30), r.fecha)
        assertEquals(LocalTime.of(10, 0), r.hora)
        assertEquals(false, r.domicilio)
    }

    @Test fun telefonoNoEsHoraYPielNoEsPie() {
        val r = leer("mi cel 099 555 666, mañana 16 hs, tengo la piel seca de las manos")
        assertEquals("099 555 666", r.telefonoEnTexto)
        assertEquals(LocalTime.of(16, 0), r.hora)
        assertEquals(listOf(3L), r.tratamientoIds)
    }

    @Test fun sinDatosNoInventa() {
        val r = leer("hola cómo estás? todo bien?")
        assertNull(r.fecha)
        assertNull(r.hora)
        assertEquals(emptyList<Long>(), r.tratamientoIds)
    }
}
