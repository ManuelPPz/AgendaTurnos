package com.example.agendaturnos.avisos

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.agendaturnos.AgendaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Recibe la alarma de un turno y muestra la notificación. */
class AvisoTurnoReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val turnoId = intent.getLongExtra(Notificaciones.EXTRA_TURNO, 0L)
        if (turnoId <= 0L) return
        Notificaciones.mostrarTurno(
            ctx,
            turnoId,
            intent.getStringExtra(EXTRA_TITULO) ?: "Turno próximo",
            intent.getStringExtra(EXTRA_TEXTO) ?: "",
        )
    }

    companion object {
        const val EXTRA_TITULO = "titulo"
        const val EXTRA_TEXTO = "texto"
    }
}

/** Revisión diaria: avisa si hay clientes para recontactar. */
class RecontactoReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pendiente = goAsync()
        val app = ctx.applicationContext as AgendaApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val lista = app.repository.clientesParaRecontactarAhora()
                Notificaciones.mostrarRecontacto(ctx, lista.map { it.cliente.nombre })
            } finally {
                pendiente.finish()
            }
        }
    }
}

/**
 * Las alarmas se borran al reiniciar el teléfono o actualizar la app:
 * acá se vuelven a programar.
 */
class ArranqueReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action !in ACCIONES) return
        val pendiente = goAsync()
        val app = ctx.applicationContext as AgendaApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.programador.programarRecontactoDiario()
                app.repository.reprogramarTodosLosAvisos()
            } finally {
                pendiente.finish()
            }
        }
    }

    private companion object {
        val ACCIONES = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
