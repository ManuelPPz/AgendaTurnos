package com.example.agendaturnos.avisos

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.agendaturnos.MainActivity
import com.example.agendaturnos.R

object Notificaciones {
    const val CANAL_TURNOS = "turnos"
    const val CANAL_RECONTACTO = "recontacto"
    const val EXTRA_TURNO = "turnoId"
    private const val ID_RECONTACTO = -1
    private const val ID_DRIVE = -2

    fun crearCanales(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL_TURNOS, "Turnos agendados", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisos 5 horas y 1 hora y media antes de cada turno"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CANAL_RECONTACTO, "Clientes para recontactar", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Aviso diario de clientes que cumplieron 20 días sin turno"
            }
        )
    }

    private fun puedeNotificar(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun abrirApp(ctx: Context, codigo: Int, turnoId: Long?): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            turnoId?.let { putExtra(EXTRA_TURNO, it) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            ctx, codigo, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun mostrarTurno(ctx: Context, turnoId: Long, titulo: String, texto: String) {
        if (!puedeNotificar(ctx)) return
        val n = NotificationCompat.Builder(ctx, CANAL_TURNOS)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(abrirApp(ctx, turnoId.toInt(), turnoId))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(turnoId.toInt(), n)
        } catch (e: SecurityException) {
            // El usuario quitó el permiso de notificaciones
        }
    }

    fun mostrarReconectarDrive(ctx: Context) {
        if (!puedeNotificar(ctx)) return
        val n = NotificationCompat.Builder(ctx, CANAL_RECONTACTO)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle("El respaldo en Google Drive se pausó")
            .setContentText("Abrí la app → Respaldo y volvé a conectar tu cuenta.")
            .setContentIntent(abrirApp(ctx, ID_DRIVE, null))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID_DRIVE, n)
        } catch (e: SecurityException) {
            // Sin permiso
        }
    }

    fun mostrarRecontacto(ctx: Context, nombres: List<String>) {
        if (!puedeNotificar(ctx) || nombres.isEmpty()) return
        val titulo = if (nombres.size == 1) "1 cliente para recontactar" else "${nombres.size} clientes para recontactar"
        val texto = nombres.take(5).joinToString(", ") + if (nombres.size > 5) "…" else ""
        val n = NotificationCompat.Builder(ctx, CANAL_RECONTACTO)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$texto\nTocá para mandarles un mensaje."))
            .setContentIntent(abrirApp(ctx, ID_RECONTACTO, null))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID_RECONTACTO, n)
        } catch (e: SecurityException) {
            // Sin permiso
        }
    }
}
