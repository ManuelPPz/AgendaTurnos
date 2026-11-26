package com.example.agendaturnos

import com.example.agendaturnos.lector.LectorMensajes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Pruebas del lector de mensajes de WhatsApp. "Hoy" es el jueves 26/11/2026.
 * Los chats imitan conversaciones reales, con nombres, teléfonos y zonas inventados.
 */
class LectorMensajesTest {
    private val catalogo = listOf(1L to "Podología", 2L to "Auriculoterapia", 3L to "Manicura")
    private val ahora = LocalDateTime.of(2026, 11, 26, 16, 30)
    private fun leer(texto: String, propios: Set<String> = emptySet()) =
        LectorMensajes.analizar(texto.trimIndent(), catalogo, ahora, propios)

    private val bienvenida = """
        [23/11, 06:56] ana gomez: Gracias por comunicarte con Ana Gomez. ¿Cómo podemos ayudarte?
        [23/11, 06:56] ana gomez: Gracias por tu mensaje. En este momento no podemos responder, pero lo haremos lo antes posible.
    """.trimIndent()

    @Test fun nombreDichoAntesDeLaPreguntaYFechaConNumero() {
        val r = leer(
            """
            [23/11, 06:56] +598 91 000 111: ¿Realizás podología a domicilio en mi zona?
            $bienvenida
            [23/11, 07:52] ana gomez: Hola buenos días si vamos a domicilio de q zona eres ? Podologia completa 1200 a las ordenes
            [23/11, 11:15] +598 91 000 111: Yo lo q tengo es un hongo en las uñas
            [23/11, 11:19] +598 91 000 111: Yo estoy en Las Flores
            [23/11, 11:39] ana gomez: Yo ya estoy agendando para diciembre 1 semana
            [23/11, 11:43] +598 91 000 111: Miércoles 2 podes?
            [23/11, 11:47] ana gomez: Si podría ser a las 10?
            [23/11, 11:49] +598 91 000 111: Mi nonbre es Carmen
            [23/11, 11:49] ana gomez: Barbaro dime tu nombre corazón
            [23/11, 11:59] +598 91 000 111: Genial gracias
            """
        )
        assertEquals("091 000 111", r.remitentes.single().telefono)
        assertEquals("Carmen", r.nombreEnTexto)
        assertEquals(LocalDate.of(2026, 12, 2), r.fecha)
        assertEquals(LocalTime.of(10, 0), r.hora)
        assertEquals(listOf(1L), r.tratamientoIds)
        assertEquals(true, r.domicilio)
        assertEquals("Las Flores", r.direccion)
    }

    @Test fun respuestaALaPreguntaPorElNombreYHoraSinSeparador() {
        val r = leer(
            """
            [20/11, 13:47] Rosa Medina: ¿Realizás podología a domicilio en mi zona?
            [20/11, 16:10] ana gomez: Podría ser para el jueves
            [20/11, 16:24] Rosa Medina: Cuanto tiempo te lleva? 10.30 u 11 te queda bien?
            [20/11, 16:24] ana gomez: Y el cuidado de los pies te lleva más o menos 1:30
            [20/11, 16:25] ana gomez: Perfecto 1030 estaría bárbaro
            [20/11, 16:25] ana gomez: Como es tu nombre corazón así te agendo
            [20/11, 16:26] Rosa Medina: Rosa Medina
            [20/11, 16:46] Rosa Medina: Vivo en las viviendas del barrio 5 casa 12.
            [25/11, 21:08] Rosa Medina: Holaa me confirmas que vienes mañana?
            [25/11, 21:17] ana gomez: Hola buenas noches si claro mañana 1030
            """,
            propios = setOf("ana gomez"),
        )
        assertEquals("Rosa Medina", r.nombreEnTexto)
        assertEquals(LocalDate.of(2026, 11, 26), r.fecha) // "mañana" dicho el 25/11
        assertEquals(LocalTime.of(10, 30), r.hora)          // no 13:30 ("te lleva 1:30")
        assertEquals("las viviendas del barrio 5 casa 12", r.direccion)
    }

    @Test fun precioNoEsHoraYUbicacionEsDomicilio() {
        val r = leer(
            """
            [26/11, 13:54] +598 92 333 444: Hola cuanto sale la consulta
            [26/11, 15:58] ana gomez: Hola buenas tardes podologia completa 1300 a las ordenes !
            [26/11, 16:03] ana gomez: Tengo turnos disponibles para el lunes nose si te queda bien a las 10 o a las 14 horas
            [26/11, 16:04] +598 92 333 444: Lunes hora 10 perfecto
            [26/11, 16:04] ana gomez: Buenísimo te agenda como es tu nombre ?
            [26/11, 16:05] +598 92 333 444: pedro ruiz
            [26/11, 16:06] ana gomez: Un día antes te envío mensaje para q me pases la ubicación
            """,
            propios = setOf("ana gomez"),
        )
        assertEquals("Pedro Ruiz", r.nombreEnTexto)
        assertEquals(LocalDate.of(2026, 11, 30), r.fecha)
        assertEquals(LocalTime.of(10, 0), r.hora)
        assertEquals(true, r.domicilio)
    }

    @Test fun avisaSiPideReagendar() {
        val r = leer(
            """
            [19/11, 22:25] Lucia: ¿Realizás podología a domicilio en mi zona?
            $bienvenida
            [19/11, 23:10] ana gomez: Para el miércoles tengo turno disponible a las 14 si te queda bien
            [20/11, 08:03] ana gomez: baebaro como es ru nombre para agendarte
            [20/11, 08:03] Lucia: Lucía Soto
            [20/11, 08:06] Lucia: Tengo callos y durezas
            [25/11, 12:46] ana gomez: tenemos agendado para hoy a las 14 podologia
            [25/11, 13:37] Lucia: Podemos reagendar
            [25/11, 13:42] Lucia: Gracias le escribo en estos dias
            """
        )
        assertEquals(listOf("Lucia"), r.remitentes.map { it.nombre })
        assertEquals("Lucía Soto", r.nombreEnTexto)
        assertTrue(r.pidioReagendar)
    }

    @Test fun mensajeSimpleSinEncabezados() {
        val r = leer("Hola! soy Laura, tenés lugar mañana a las 15 para manicura?")
        assertEquals("Laura", r.nombreEnTexto)
        assertEquals(LocalDate.of(2026, 11, 27), r.fecha)
        assertEquals(LocalTime.of(15, 0), r.hora)
        assertEquals(listOf(3L), r.tratamientoIds)
        assertFalse(r.pidioReagendar)
    }

    @Test fun deLaMananaNoEsMananaYConsultorio() {
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
