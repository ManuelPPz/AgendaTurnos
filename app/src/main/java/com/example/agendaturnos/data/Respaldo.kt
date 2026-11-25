package com.example.agendaturnos.data

import android.content.Context
import android.net.Uri
import com.example.agendaturnos.data.db.AgendaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Respaldo completo en un .zip: la base de datos (clientes, turnos, historial)
 * y las fotos. Se puede guardar en Drive, mandar por WhatsApp o por mail.
 */
object Respaldo {
    private const val NOMBRE_DB = "agenda.db"
    private const val CARPETA_FOTOS = "fotos"
    private const val PREFS = "respaldo"
    private const val CLAVE_ULTIMO = "ultimo"

    suspend fun exportar(ctx: Context, db: AgendaDatabase, destino: Uri) = withContext(Dispatchers.IO) {
        // Vuelca al archivo principal los cambios que SQLite tiene en el diario (WAL).
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val archivoDb = ctx.getDatabasePath(NOMBRE_DB)
        val salida = ctx.contentResolver.openOutputStream(destino) ?: error("No se pudo crear el archivo")
        ZipOutputStream(salida).use { zip ->
            zip.putNextEntry(ZipEntry(NOMBRE_DB))
            archivoDb.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            File(ctx.filesDir, CARPETA_FOTOS).listFiles()?.filter { it.isFile }?.forEach { foto ->
                zip.putNextEntry(ZipEntry("$CARPETA_FOTOS/${foto.name}"))
                foto.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(CLAVE_ULTIMO, System.currentTimeMillis()).apply()
    }

    /**
     * Reemplaza TODOS los datos actuales por los del respaldo.
     * Devuelve false si el archivo no es un respaldo válido (en ese caso no se toca nada).
     * Después de restaurar hay que reiniciar la app.
     */
    suspend fun restaurar(ctx: Context, db: AgendaDatabase, origen: Uri): Boolean = withContext(Dispatchers.IO) {
        val temporal = File(ctx.cacheDir, "restaurar").apply { deleteRecursively(); mkdirs() }
        val entrada = ctx.contentResolver.openInputStream(origen) ?: return@withContext false
        ZipInputStream(entrada).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val nombre = e.name
                val valido = nombre == NOMBRE_DB ||
                    (nombre.startsWith("$CARPETA_FOTOS/") && !nombre.contains("..") && nombre.count { it == '/' } == 1)
                if (!e.isDirectory && valido) {
                    val destino = File(temporal, nombre)
                    destino.parentFile?.mkdirs()
                    destino.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                e = zip.nextEntry
            }
        }
        val nuevaDb = File(temporal, NOMBRE_DB)
        if (!nuevaDb.exists() || !esSqlite(nuevaDb)) {
            temporal.deleteRecursively()
            return@withContext false
        }

        db.close()
        val archivoDb = ctx.getDatabasePath(NOMBRE_DB)
        listOf("", "-wal", "-shm").forEach { File(archivoDb.path + it).delete() }
        nuevaDb.copyTo(archivoDb, overwrite = true)

        val fotos = File(ctx.filesDir, CARPETA_FOTOS)
        fotos.deleteRecursively()
        File(temporal, CARPETA_FOTOS).takeIf { it.exists() }?.copyRecursively(fotos, overwrite = true)
        temporal.deleteRecursively()
        true
    }

    fun ultimoRespaldo(ctx: Context): LocalDateTime? {
        val millis = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(CLAVE_ULTIMO, 0L)
        return if (millis == 0L) null else LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    }

    private fun esSqlite(f: File): Boolean = f.inputStream().use {
        val cabecera = ByteArray(15)
        it.read(cabecera) == 15 && String(cabecera, Charsets.US_ASCII) == "SQLite format 3"
    }
}
