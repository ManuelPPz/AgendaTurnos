package com.example.agendaturnos.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.agendaturnos.data.model.Aviso
import com.example.agendaturnos.data.model.Cliente
import com.example.agendaturnos.data.model.Foto
import com.example.agendaturnos.data.model.TratamientoCatalogo
import com.example.agendaturnos.data.model.Turno
import com.example.agendaturnos.data.model.TurnoConDetalle
import com.example.agendaturnos.data.model.TurnoTratamiento
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface ClienteDao {
    @Insert
    suspend fun insertar(cliente: Cliente): Long

    @Update
    suspend fun actualizar(cliente: Cliente)

    @Query("SELECT * FROM clientes WHERE id = :id")
    suspend fun obtener(id: Long): Cliente?

    @Query("SELECT * FROM clientes WHERE id = :id")
    fun observar(id: Long): Flow<Cliente?>

    @Query("SELECT * FROM clientes WHERE activo = 1 ORDER BY nombreBusqueda")
    fun observarActivos(): Flow<List<Cliente>>

    /** [texto] tiene que venir normalizado (minúsculas, sin acentos). */
    @Query(
        """
        SELECT * FROM clientes
        WHERE activo = 1
          AND (nombreBusqueda LIKE '%' || :texto || '%' OR telefono LIKE '%' || :texto || '%')
        ORDER BY nombreBusqueda
        LIMIT 20
        """
    )
    fun buscar(texto: String): Flow<List<Cliente>>

    @Query(
        """
        SELECT * FROM clientes
        WHERE activo = 1
          AND (nombreBusqueda = :nombreBusqueda OR (:telefono != '' AND telefono = :telefono))
        """
    )
    suspend fun posiblesDuplicados(nombreBusqueda: String, telefono: String): List<Cliente>
}

@Dao
interface TratamientoDao {
    @Insert
    suspend fun insertar(tratamiento: TratamientoCatalogo): Long

    @Update
    suspend fun actualizar(tratamiento: TratamientoCatalogo)

    @Query("SELECT * FROM tratamientos WHERE activo = 1 ORDER BY nombre")
    fun observarActivos(): Flow<List<TratamientoCatalogo>>

    @Query("SELECT * FROM tratamientos WHERE activo = 1 ORDER BY nombre")
    suspend fun listarActivos(): List<TratamientoCatalogo>

    @Query("SELECT * FROM tratamientos ORDER BY activo DESC, nombre")
    fun observarTodos(): Flow<List<TratamientoCatalogo>>
}

@Dao
abstract class TurnoDao {
    @Insert
    abstract suspend fun insertarTurno(turno: Turno): Long

    @Insert
    abstract suspend fun insertarItems(items: List<TurnoTratamiento>)

    @Update
    abstract suspend fun actualizarTurno(turno: Turno)

    @Query("DELETE FROM turno_tratamientos WHERE turnoId = :turnoId")
    abstract suspend fun borrarItems(turnoId: Long)

    /** Crea el turno y sus tratamientos juntos. */
    @Transaction
    open suspend fun crearTurno(turno: Turno, items: List<TurnoTratamiento>): Long {
        val id = insertarTurno(turno)
        insertarItems(items.map { it.copy(id = 0, turnoId = id) })
        return id
    }

    /** Reemplaza los tratamientos de un turno (al editar o al cerrar con otro precio). */
    @Transaction
    open suspend fun reemplazarItems(turnoId: Long, items: List<TurnoTratamiento>) {
        borrarItems(turnoId)
        insertarItems(items.map { it.copy(id = 0, turnoId = turnoId) })
    }

    @Transaction
    @Query("SELECT * FROM turnos WHERE id = :id")
    abstract fun observarDetalle(id: Long): Flow<TurnoConDetalle?>

    @Transaction
    @Query("SELECT * FROM turnos WHERE estado = 'AGENDADO' AND fechaHora >= :desde ORDER BY fechaHora")
    abstract fun observarProximos(desde: LocalDateTime): Flow<List<TurnoConDetalle>>

    @Transaction
    @Query("SELECT * FROM turnos WHERE estado = 'AGENDADO' AND fechaHora < :hasta ORDER BY fechaHora")
    abstract fun observarPorCerrar(hasta: LocalDateTime): Flow<List<TurnoConDetalle>>

    @Transaction
    @Query("SELECT * FROM turnos WHERE clienteId = :clienteId AND estado IN (:estados) ORDER BY fechaHora DESC")
    abstract fun observarHistorial(clienteId: Long, estados: List<String>): Flow<List<TurnoConDetalle>>

    @Transaction
    @Query("SELECT * FROM turnos WHERE clienteId = :clienteId AND estado = 'TERMINADO' ORDER BY fechaHora DESC LIMIT 1")
    abstract suspend fun ultimoTerminado(clienteId: Long): TurnoConDetalle?

    @Query("SELECT COUNT(*) FROM turnos WHERE clienteId = :clienteId AND estado = 'AGENDADO' AND fechaHora >= :desde")
    abstract suspend fun contarFuturos(clienteId: Long, desde: LocalDateTime): Int

    @Query("SELECT COUNT(*) FROM turnos WHERE estado = 'AGENDADO' AND fechaHora = :fechaHora AND id != :excluirId")
    abstract suspend fun contarEnMismoHorario(fechaHora: LocalDateTime, excluirId: Long = 0): Int

    @Transaction
    @Query("SELECT * FROM turnos WHERE clienteId = :clienteId AND estado = 'AGENDADO' AND fechaHora >= :desde ORDER BY fechaHora LIMIT 1")
    abstract fun observarProximoDeCliente(clienteId: Long, desde: LocalDateTime): Flow<TurnoConDetalle?>

    @Transaction
    @Query("SELECT * FROM turnos WHERE clienteId = :clienteId AND estado = 'TERMINADO' ORDER BY fechaHora DESC LIMIT 1")
    abstract fun observarUltimoTerminado(clienteId: Long): Flow<TurnoConDetalle?>

    @Query("SELECT COUNT(*) FROM turnos WHERE clienteId = :clienteId AND estado = 'TERMINADO'")
    abstract fun observarCantidadVisitas(clienteId: Long): Flow<Int>
}

@Dao
interface FotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(foto: Foto): Long

    @Delete
    suspend fun borrar(foto: Foto)

    @Query("SELECT * FROM fotos WHERE turnoId = :turnoId")
    suspend fun deTurno(turnoId: Long): List<Foto>
}

@Dao
interface AvisoDao {
    @Insert
    suspend fun insertar(aviso: Aviso): Long

    @Update
    suspend fun actualizar(aviso: Aviso)

    @Query("SELECT * FROM avisos WHERE estado IN ('PENDIENTE', 'POSPUESTO') ORDER BY fechaProgramada")
    fun observarPendientes(): Flow<List<Aviso>>

    @Query("SELECT * FROM avisos WHERE turnoId = :turnoId")
    suspend fun deTurno(turnoId: Long): List<Aviso>

    @Query("DELETE FROM avisos WHERE turnoId = :turnoId")
    suspend fun borrarDeTurno(turnoId: Long)
}
