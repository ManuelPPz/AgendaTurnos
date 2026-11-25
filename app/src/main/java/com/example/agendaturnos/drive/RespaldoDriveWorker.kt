package com.example.agendaturnos.drive

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.agendaturnos.AgendaApp
import com.example.agendaturnos.avisos.Notificaciones
import java.util.concurrent.TimeUnit

/** Sube el respaldo a Google Drive en segundo plano (una vez por día y después de cerrar turnos). */
class RespaldoDriveWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (DriveRespaldo.cuenta(ctx) == null) return Result.success()
        return try {
            val autorizacion = DriveRespaldo.autorizar(ctx)
            val token = autorizacion.accessToken
            if (autorizacion.hasResolution() || token == null) {
                // Google pide volver a dar permiso: solo se puede desde la app.
                Notificaciones.mostrarReconectarDrive(ctx)
                Result.success()
            } else {
                DriveRespaldo.subir(ctx, (ctx as AgendaApp).database, token)
                Result.success()
            }
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val DIARIO = "respaldo-drive-diario"
        private const val AHORA = "respaldo-drive-ahora"

        private val conInternet = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun programarDiario(ctx: Context) {
            val pedido = PeriodicWorkRequestBuilder<RespaldoDriveWorker>(1, TimeUnit.DAYS)
                .setConstraints(conInternet)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(DIARIO, ExistingPeriodicWorkPolicy.KEEP, pedido)
        }

        /** Respaldo inmediato (si hay cuenta conectada). Se agrupan pedidos seguidos. */
        fun respaldarAhora(ctx: Context) {
            if (DriveRespaldo.cuenta(ctx) == null) return
            val pedido = OneTimeWorkRequestBuilder<RespaldoDriveWorker>()
                .setConstraints(conInternet)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(AHORA, ExistingWorkPolicy.REPLACE, pedido)
        }

        fun cancelar(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(DIARIO)
            WorkManager.getInstance(ctx).cancelUniqueWork(AHORA)
        }
    }
}
