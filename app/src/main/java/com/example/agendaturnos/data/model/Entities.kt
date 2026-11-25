package com.example.agendaturnos.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

/** Precios en pesos enteros (sin centésimos). */

@Entity(
    tableName = "clientes",
    indices = [Index("nombreBusqueda"), Index("telefono")],
)
data class Cliente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    /** Nombre en minúsculas y sin acentos, para buscar. Lo completa el repositorio. */
    val nombreBusqueda: String = "",
    val telefono: String,
    /** Dirección de atención a domicilio. Opcional. */
    val direccion: String? = null,
    val genero: Genero? = null,
    val fechaNacimiento: LocalDate? = null,
    val preferencias: String? = null,
    val diasRecontacto: Int = 20,
    /** Los clientes no se borran: se archivan para no perder el historial. */
    val activo: Boolean = true,
    val creadoEn: LocalDateTime = LocalDateTime.now(),
)

@Entity(tableName = "tratamientos")
data class TratamientoCatalogo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val precioBase: Long = 0,
    val duracionMin: Int? = null,
    val activo: Boolean = true,
)

@Entity(
    tableName = "turnos",
    foreignKeys = [
        ForeignKey(
            entity = Cliente::class,
            parentColumns = ["id"],
            childColumns = ["clienteId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("clienteId"), Index("fechaHora"), Index("estado")],
)
data class Turno(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clienteId: Long = 0,
    val fechaHora: LocalDateTime,
    val lugar: Lugar,
    /** Solo si es a domicilio. Se copia del cliente y se puede editar. */
    val direccion: String? = null,
    val notas: String? = null,
    val estado: EstadoTurno = EstadoTurno.AGENDADO,
    val motivoCancelacion: String? = null,
    val cerradoEn: LocalDateTime? = null,
)

/** Cada tratamiento dentro de un turno, con el precio de ese momento. */
@Entity(
    tableName = "turno_tratamientos",
    foreignKeys = [
        ForeignKey(
            entity = Turno::class,
            parentColumns = ["id"],
            childColumns = ["turnoId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TratamientoCatalogo::class,
            parentColumns = ["id"],
            childColumns = ["tratamientoId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("turnoId"), Index("tratamientoId")],
)
data class TurnoTratamiento(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val turnoId: Long = 0,
    val tratamientoId: Long,
    val precioCobrado: Long,
    val descripcion: String? = null,
)

/** Fotos opcionales: como máximo una de antes y una de después por turno. */
@Entity(
    tableName = "fotos",
    foreignKeys = [
        ForeignKey(
            entity = Turno::class,
            parentColumns = ["id"],
            childColumns = ["turnoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["turnoId", "tipo"], unique = true)],
)
data class Foto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val turnoId: Long,
    val tipo: TipoFoto,
    /** Ruta del archivo en el almacenamiento interno de la app. */
    val rutaArchivo: String,
    val creadaEn: LocalDateTime = LocalDateTime.now(),
)

@Entity(
    tableName = "avisos",
    foreignKeys = [
        ForeignKey(
            entity = Cliente::class,
            parentColumns = ["id"],
            childColumns = ["clienteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Turno::class,
            parentColumns = ["id"],
            childColumns = ["turnoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("clienteId"), Index("turnoId"), Index("fechaProgramada")],
)
data class Aviso(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipo: TipoAviso,
    val clienteId: Long,
    val turnoId: Long? = null,
    val fechaProgramada: LocalDateTime,
    val estado: EstadoAviso = EstadoAviso.PENDIENTE,
)
