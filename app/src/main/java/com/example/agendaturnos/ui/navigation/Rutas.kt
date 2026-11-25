package com.example.agendaturnos.ui.navigation

object Rutas {
    const val INICIO = "inicio"
    const val BUSCAR = "buscar"
    const val NUEVO_CLIENTE = "nuevo_cliente"
    const val AGENDAR = "agendar?clienteId={clienteId}"
    const val CATALOGO = "catalogo"

    const val TURNO = "turno/{turnoId}"
    const val EDITAR_TURNO = "editar_turno/{turnoId}"
    const val CERRAR_TURNO = "cerrar/{turnoId}"
    const val DETALLE_TRATAMIENTO = "detalle/{turnoId}"
    const val CLIENTE = "cliente/{clienteId}"
    const val CLIENTE_FORM = "cliente_form/{clienteId}"
    const val HISTORIAL = "historial/{clienteId}"

    fun turno(id: Long) = "turno/$id"
    fun editarTurno(id: Long) = "editar_turno/$id"
    /** clienteId = 0 para elegir el cliente en la pantalla. */
    fun agendar(clienteId: Long = 0L) = "agendar?clienteId=$clienteId"
    fun cerrarTurno(id: Long) = "cerrar/$id"
    fun detalleTratamiento(id: Long) = "detalle/$id"
    fun cliente(id: Long) = "cliente/$id"
    /** id = 0 para un cliente nuevo. */
    fun clienteForm(id: Long) = "cliente_form/$id"
    fun historial(id: Long) = "historial/$id"
}
