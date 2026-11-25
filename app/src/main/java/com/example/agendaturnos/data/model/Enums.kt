package com.example.agendaturnos.data.model

enum class Genero { MASCULINO, FEMENINO }

/** Dónde se atiende el turno. */
enum class Lugar { DOMICILIO, CONSULTORIO }

/**
 * Estado guardado del turno.
 * "Por cerrar" no se guarda: es un turno AGENDADO cuya fecha ya pasó.
 */
enum class EstadoTurno { AGENDADO, TERMINADO, CANCELADO }

enum class TipoFoto { ANTES, DESPUES }

enum class TipoAviso { TURNO, RECONTACTO, CUMPLEANOS }

enum class EstadoAviso { PENDIENTE, ENVIADO, DESCARTADO, POSPUESTO }
