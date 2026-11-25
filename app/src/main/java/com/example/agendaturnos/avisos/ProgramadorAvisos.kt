package com.example.agendaturnos.avisos

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.agendaturnos.data.model.EstadoTurno
import com.example.agendaturnos.data.model.Lugar
import com.example.agendaturnos.data.model.TurnoConDetalle
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Programa las alarmas del sistema que disparan los avisos. */
class ProgramadorAvisos(private val ctx: Context) {
    private val alarmas = ctx.getSystemService(AlarmManager::class.java)
    private val formatoHora = DateTimeFormatter.ofPattern("HH:mm")

    /** Minutos de anticipación y título de cada aviso de turno. */
    private val anticipaciones = listOf(
        300L to "Turno en 5 horas",
        90L to "Turno en 1 h 30",
    )

    /** En Android 12+ las alarmas exactas necesitan permiso del usuario. */
    fun puedeExactas(): Boolean = Build.VERSION.SDK_INT < 31 || alarmas.canScheduleExactAlarms()

    fun programarTurno(d: TurnoConDetalle) {
        cancelarTurno(d.turno.id)
        if (d.turno.estado != EstadoTurno.AGENDADO) return
        val ahora = LocalDateTime.now()
        val lugar = if (d.turno.lugar == Lugar.DOMICILIO) "A domicilio" else "En consultorio"
        val texto = "${d.turno.fechaHora.format(formatoHora)} · ${d.cliente.nombre} · ${d.resumenTratamientos} · $lugar"
        anticipaciones.forEachIndexed { i, (minutos, titulo) ->
            val cuando = d.turno.fechaHora.minusMinutes(minutos)
            if (cuando.isAfter(ahora)) {
                programar(aMillis(cuando), intentTurno(d.turno.id, i, titulo, texto))
            }
        }
    }

    fun cancelarTurno(turnoId: Long) {
        anticipaciones.indices.forEach { i -> alarmas.cancel(intentTurno(turnoId, i, null, null)) }
    }

    /** Revisión diaria (10:00 aprox.) de clientes para recontactar. */
    fun programarRecontactoDiario() {
        var primera = LocalDate.now().atTime(10, 0)
        if (!primera.isAfter(LocalDateTime.now())) primera = primera.plusDays(1)
        alarmas.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, aMillis(primera), AlarmManager.INTERVAL_DAY, intentRecontacto(),
        )
    }

    private fun programar(millis: Long, pi: PendingIntent) {
        try {
            if (puedeExactas()) {
                alarmas.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                alarmas.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        } catch (e: SecurityException) {
            alarmas.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun aMillis(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun intentTurno(turnoId: Long, indice: Int, titulo: String?, texto: String?): PendingIntent {
        val intent = Intent(ctx, AvisoTurnoReceiver::class.java).apply {
            data = Uri.parse("agenda://turno/$turnoId/$indice")
            putExtra(Notificaciones.EXTRA_TURNO, turnoId)
            titulo?.let { putExtra(AvisoTurnoReceiver.EXTRA_TITULO, it) }
            texto?.let { putExtra(AvisoTurnoReceiver.EXTRA_TEXTO, it) }
        }
        return PendingIntent.getBroadcast(
            ctx, (turnoId * 2 + indice).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun intentRecontacto(): PendingIntent = PendingIntent.getBroadcast(
        ctx, Int.MAX_VALUE, Intent(ctx, RecontactoReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
