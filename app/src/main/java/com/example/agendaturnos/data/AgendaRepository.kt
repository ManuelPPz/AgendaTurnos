package com.example.agendaturnos.data

import androidx.room.withTransaction
import com.example.agendaturnos.data.db.AgendaDatabase
import com.example.agendaturnos.data.model.Cliente
import com.example.agendaturnos.data.model.EstadoTurno
import com.example.agendaturnos.data.model.Genero
import com.example.agendaturnos.data.model.Lugar
import com.example.agendaturnos.data.model.TratamientoCatalogo
import com.example.agendaturnos.data.model.Turno
import com.example.agendaturnos.data.model.TurnoConDetalle
import com.example.agendaturnos.data.model.TurnoTratamiento
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Punto único de acceso a los datos para las pantallas. */
class AgendaRepository(private val db: AgendaDatabase) {
    private val clientes = db.clienteDao()
    private val tratamientos = db.tratamientoDao()
    private val turnos = db.turnoDao()

    // ---------- Clientes ----------

    fun buscarClientes(texto: String): Flow<List<Cliente>> =
        if (texto.isBlank()) clientes.observarActivos() else clientes.buscar(normalizar(texto))

    fun observarCliente(id: Long): Flow<Cliente?> = clientes.observar(id)

    suspend fun obtenerCliente(id: Long): Cliente? = clientes.obtener(id)

    /** Archiva en vez de borrar: deja de aparecer en búsquedas pero conserva el historial. */
    suspend fun archivarCliente(id: Long) {
        clientes.obtener(id)?.let { clientes.actualizar(it.copy(activo = false)) }
    }

    fun proximoTurnoDeCliente(clienteId: Long): Flow<TurnoConDetalle?> =
        turnos.observarProximoDeCliente(clienteId, LocalDateTime.now())

    fun ultimoTerminadoDeCliente(clienteId: Long): Flow<TurnoConDetalle?> =
        turnos.observarUltimoTerminado(clienteId)

    fun cantidadVisitas(clienteId: Long): Flow<Int> = turnos.observarCantidadVisitas(clienteId)

    suspend fun posiblesDuplicados(nombre: String, telefono: String): List<Cliente> =
        clientes.posiblesDuplicados(normalizar(nombre), telefono.trim())

    suspend fun guardarCliente(cliente: Cliente): Long {
        val listo = cliente.copy(nombreBusqueda = normalizar(cliente.nombre))
        return if (listo.id == 0L) clientes.insertar(listo) else {
            clientes.actualizar(listo); listo.id
        }
    }

    /** Pantalla "Nuevo cliente + turno": crea los dos juntos. Devuelve el id del turno. */
    suspend fun crearClienteConTurno(
        cliente: Cliente,
        turno: Turno,
        items: List<TurnoTratamiento>,
    ): Long = db.withTransaction {
        val clienteId = guardarCliente(cliente)
        turnos.crearTurno(turno.copy(clienteId = clienteId), items)
    }

    // ---------- Tratamientos ----------

    fun tratamientosActivos(): Flow<List<TratamientoCatalogo>> = tratamientos.observarActivos()

    fun todosLosTratamientos(): Flow<List<TratamientoCatalogo>> = tratamientos.observarTodos()

    suspend fun guardarTratamiento(t: TratamientoCatalogo) {
        if (t.id == 0L) tratamientos.insertar(t) else tratamientos.actualizar(t)
    }

    // ---------- Turnos ----------

    suspend fun agendar(turno: Turno, items: List<TurnoTratamiento>): Long =
        turnos.crearTurno(turno, items)

    fun proximosTurnos(desde: LocalDateTime = LocalDateTime.now()): Flow<List<TurnoConDetalle>> =
        turnos.observarProximos(desde)

    fun turnosPorCerrar(hasta: LocalDateTime = LocalDateTime.now()): Flow<List<TurnoConDetalle>> =
        turnos.observarPorCerrar(hasta)

    fun observarTurno(id: Long): Flow<TurnoConDetalle?> = turnos.observarDetalle(id)

    suspend fun obtenerTurno(id: Long): TurnoConDetalle? = turnos.observarDetalle(id).first()

    /** Editar o reprogramar: actualiza el turno y reemplaza sus tratamientos. */
    suspend fun actualizarTurno(turno: Turno, items: List<TurnoTratamiento>) = db.withTransaction {
        turnos.actualizarTurno(turno)
        turnos.reemplazarItems(turno.id, items)
    }

    suspend fun cancelarTurno(id: Long, motivo: String?) {
        val d = obtenerTurno(id) ?: return
        turnos.actualizarTurno(
            d.turno.copy(
                estado = EstadoTurno.CANCELADO,
                motivoCancelacion = motivo?.trim()?.ifBlank { null },
                cerradoEn = LocalDateTime.now(),
            )
        )
    }

    fun historial(clienteId: Long, incluirCancelados: Boolean = false): Flow<List<TurnoConDetalle>> =
        turnos.observarHistorial(
            clienteId,
            (if (incluirCancelados) listOf(EstadoTurno.TERMINADO, EstadoTurno.CANCELADO)
            else listOf(EstadoTurno.TERMINADO)).map { it.name },
        )

    suspend fun ultimoTerminado(clienteId: Long): TurnoConDetalle? = turnos.ultimoTerminado(clienteId)

    suspend fun hayTurnoEnMismoHorario(fechaHora: LocalDateTime, excluirId: Long = 0): Boolean =
        turnos.contarEnMismoHorario(fechaHora, excluirId) > 0

    // ---------- Datos de prueba (solo para desarrollo) ----------

    /**
     * Carga clientes y turnos de ejemplo: visitas pasadas, un cancelado,
     * turnos por cerrar y próximos. Devuelve false si ya estaban cargados.
     */
    suspend fun cargarDatosDePrueba(): Boolean = db.withTransaction {
        if (clientes.posiblesDuplicados(normalizar("María González"), "099 101 201").isNotEmpty()) {
            return@withTransaction false
        }
        val catalogo = tratamientos.listarActivos().associateBy { it.nombre }
        val pod = catalogo.getValue("Podología").id
        val aur = catalogo.getValue("Auriculoterapia").id
        val man = catalogo.getValue("Manicura").id

        val ids = listOf(
            ClientePrueba("María González", "099 101 201", "Av. Italia 2345, apto 3", Genero.FEMENINO, "1958-03-12", "Pies sensibles, prefiere turnos de mañana"),
            ClientePrueba("Ana Rodríguez", "098 202 302", null, Genero.FEMENINO, "1985-07-21", null),
            ClientePrueba("Lucía Fernández", "094 303 403", "Bv. Artigas 1120", Genero.FEMENINO, null, "Diabética: cuidado especial en podología"),
            ClientePrueba("Carlos Pereira", "091 404 504", "Rivera 3456", Genero.MASCULINO, "1949-11-02", "Usa bastón, atender en planta baja"),
            ClientePrueba("Sofía Martínez", "097 505 605", null, Genero.FEMENINO, "1992-01-30", "Esmalte semipermanente, tonos nude"),
            ClientePrueba("Jorge López", "099 606 706", "8 de Octubre 2890", Genero.MASCULINO, null, null),
            ClientePrueba("Valentina Silva", "095 707 807", null, Genero.FEMENINO, "2000-09-14", null),
            ClientePrueba("Rosa Méndez", "092 808 908", "Garibaldi 2210", Genero.FEMENINO, "1952-05-05", "Auriculoterapia para la ansiedad"),
        ).map { c ->
            guardarCliente(
                Cliente(
                    nombre = c.nombre, telefono = c.tel, direccion = c.dir, genero = c.g,
                    fechaNacimiento = c.nac?.let(LocalDate::parse), preferencias = c.pref,
                )
            ) to c.dir
        }

        val hoy = LocalDate.now()
        val pendientes = mutableListOf<Pair<Turno, List<TurnoTratamiento>>>()
        fun turno(
            cliente: Int, dias: Long, hora: String, lugar: Lugar,
            items: List<Pair<Long, Long>>, estado: EstadoTurno = EstadoTurno.AGENDADO, motivo: String? = null,
        ) {
            val (clienteId, dir) = ids[cliente]
            val fecha = hoy.plusDays(dias).atTime(LocalTime.parse(hora))
            val t = Turno(
                clienteId = clienteId, fechaHora = fecha, lugar = lugar,
                direccion = if (lugar == Lugar.DOMICILIO) dir else null,
                estado = estado, motivoCancelacion = motivo,
                cerradoEn = if (estado == EstadoTurno.AGENDADO) null else fecha.plusHours(1),
            )
            pendientes += t to items.map { (trat, precio) -> TurnoTratamiento(tratamientoId = trat, precioCobrado = precio) }
        }
        val D = Lugar.DOMICILIO
        val K = Lugar.CONSULTORIO
        val T = EstadoTurno.TERMINADO

        // Visitas ya hechas
        turno(0, -45, "10:00", D, listOf(pod to 900L), T)
        turno(0, -22, "10:00", D, listOf(pod to 900L), T)
        turno(1, -30, "16:00", K, listOf(man to 500L), T)
        turno(2, -15, "11:00", D, listOf(pod to 1000L), T)
        turno(3, -25, "09:30", D, listOf(pod to 900L, aur to 700L), T)
        turno(4, -10, "17:00", K, listOf(man to 600L), T)
        turno(7, -21, "15:00", D, listOf(aur to 700L), T)
        // Cancelado
        turno(5, -5, "14:00", K, listOf(pod to 900L), EstadoTurno.CANCELADO, "No podía ese día")
        // Por cerrar (fecha pasada, sin cerrar)
        turno(1, -1, "15:00", K, listOf(man to 500L))
        turno(6, -2, "10:00", K, listOf(aur to 700L))
        // Próximos
        turno(2, 1, "09:00", D, listOf(pod to 1000L))
        turno(4, 1, "17:30", K, listOf(man to 600L))
        turno(5, 2, "11:00", K, listOf(pod to 900L, man to 500L))
        turno(7, 3, "16:00", D, listOf(aur to 700L))
        turno(3, 6, "10:00", D, listOf(pod to 900L))
        turno(6, 9, "18:00", K, listOf(man to 500L))

        pendientes.forEach { (t, items) -> turnos.crearTurno(t, items) }
        true
    }

    companion object {
        /** "Ana María Pérez" -> "ana maria perez", para buscar sin acentos ni mayúsculas. */
        fun normalizar(texto: String): String =
            Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase()
    }
}

private data class ClientePrueba(
    val nombre: String,
    val tel: String,
    val dir: String?,
    val g: Genero?,
    val nac: String?,
    val pref: String?,
)
