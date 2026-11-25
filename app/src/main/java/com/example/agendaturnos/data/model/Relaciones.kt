package com.example.agendaturnos.data.model

import androidx.room.Embedded
import androidx.room.Relation
import java.time.LocalDateTime

data class ItemConTratamiento(
    @Embedded val item: TurnoTratamiento,
    @Relation(parentColumn = "tratamientoId", entityColumn = "id")
    val tratamiento: TratamientoCatalogo,
)

/** Un turno con su cliente, sus tratamientos y sus fotos. */
data class TurnoConDetalle(
    @Embedded val turno: Turno,
    @Relation(parentColumn = "clienteId", entityColumn = "id")
    val cliente: Cliente,
    @Relation(
        entity = TurnoTratamiento::class,
        parentColumn = "id",
        entityColumn = "turnoId",
    )
    val items: List<ItemConTratamiento>,
    @Relation(parentColumn = "id", entityColumn = "turnoId")
    val fotos: List<Foto>,
) {
    val total: Long get() = items.sumOf { it.item.precioCobrado }

    val resumenTratamientos: String
        get() = items.joinToString(" + ") { it.tratamiento.nombre }

    fun estaPorCerrar(ahora: LocalDateTime = LocalDateTime.now()): Boolean =
        turno.estado == EstadoTurno.AGENDADO && turno.fechaHora.isBefore(ahora)
}
