package com.example.agendaturnos.drive

import android.accounts.Account
import android.content.Context
import com.example.agendaturnos.data.Respaldo
import com.example.agendaturnos.data.db.AgendaDatabase
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Respaldo en la carpeta privada de la app en Google Drive (appDataFolder).
 * Solo esta app puede ver esos archivos; no ocupan lugar visible en el Drive del usuario.
 */
object DriveRespaldo {
    private const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    private const val NOMBRE_ARCHIVO = "agenda-respaldo.zip"
    /** Cuántas versiones anteriores se conservan en Drive. */
    private const val VERSIONES = 3

    private const val PREFS = "drive"
    private const val CLAVE_CUENTA = "cuenta"
    private const val CLAVE_ULTIMA_SUBIDA = "ultimaSubida"

    data class ArchivoDrive(val id: String, val modificado: Instant, val bytes: Long) {
        val fecha: LocalDateTime get() = LocalDateTime.ofInstant(modificado, ZoneId.systemDefault())
    }

    class DriveException(val codigo: Int, mensaje: String) : IOException(mensaje)

    // ---------- Cuenta conectada ----------

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Email de la cuenta de Google conectada, o null si no hay. */
    fun cuenta(ctx: Context): String? = prefs(ctx).getString(CLAVE_CUENTA, null)

    /** Guarda la cuenta conectada. Si no se pudo saber el email, queda "" (conectada igual). */
    fun guardarCuenta(ctx: Context, email: String?) {
        prefs(ctx).edit().putString(CLAVE_CUENTA, email.orEmpty()).apply()
    }

    fun desconectar(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    fun ultimaSubida(ctx: Context): LocalDateTime? {
        val millis = prefs(ctx).getLong(CLAVE_ULTIMA_SUBIDA, 0L)
        return if (millis == 0L) null else LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    }

    // ---------- Autorización ----------

    /**
     * Pide acceso a la carpeta de la app en Drive. Si el usuario ya lo dio, devuelve el token
     * directamente; si no, el resultado trae un PendingIntent para mostrar la pantalla de Google.
     */
    suspend fun autorizar(ctx: Context, email: String? = cuenta(ctx)): AuthorizationResult {
        val solicitud = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE_APPDATA)))
            .apply { email?.takeIf { it.isNotBlank() }?.let { setAccount(Account(it, "com.google")) } }
            .build()
        return Identity.getAuthorizationClient(ctx).authorize(solicitud).esperar()
    }

    private suspend fun <T> Task<T>.esperar(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }

    // ---------- Operaciones con Drive (API REST v3) ----------

    /** Email de la cuenta dueña del token (para mostrarlo y recordarlo). */
    suspend fun emailDeCuenta(token: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(pedir("GET", "https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)", token))
            json.optJSONObject("user")?.optString("emailAddress")?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /** Respaldos guardados en Drive, del más nuevo al más viejo. */
    suspend fun listar(token: String): List<ArchivoDrive> = withContext(Dispatchers.IO) {
        val params = "spaces=appDataFolder" +
            "&orderBy=" + URLEncoder.encode("modifiedTime desc", "UTF-8") +
            "&fields=" + URLEncoder.encode("files(id,name,modifiedTime,size)", "UTF-8") +
            "&pageSize=50"
        val json = JSONObject(pedir("GET", "https://www.googleapis.com/drive/v3/files?$params", token))
        val archivos = json.optJSONArray("files") ?: return@withContext emptyList()
        (0 until archivos.length()).map { i ->
            val f = archivos.getJSONObject(i)
            ArchivoDrive(
                id = f.getString("id"),
                modificado = Instant.parse(f.getString("modifiedTime")),
                bytes = f.optString("size", "0").toLongOrNull() ?: 0L,
            )
        }
    }

    /** Arma el .zip y lo sube. Borra las versiones viejas que sobran. */
    suspend fun subir(ctx: Context, db: AgendaDatabase, token: String) = withContext(Dispatchers.IO) {
        val zip = File(ctx.cacheDir, "subida-drive.zip")
        try {
            zip.outputStream().use { Respaldo.exportarA(ctx, db, it) }
            subirArchivo(zip, token)
        } finally {
            zip.delete()
        }
        listar(token).drop(VERSIONES).forEach { viejo ->
            runCatching { pedir("DELETE", "https://www.googleapis.com/drive/v3/files/${viejo.id}", token) }
        }
        val ahora = System.currentTimeMillis()
        prefs(ctx).edit().putLong(CLAVE_ULTIMA_SUBIDA, ahora).apply()
        Respaldo.marcarRespaldo(ctx)
    }

    /** Descarga un respaldo de Drive y reemplaza los datos locales. Después hay que reiniciar la app. */
    suspend fun restaurar(ctx: Context, db: AgendaDatabase, token: String, archivo: ArchivoDrive): Boolean =
        withContext(Dispatchers.IO) {
            val destino = File(ctx.cacheDir, "descarga-drive.zip")
            try {
                val con = abrir("GET", "https://www.googleapis.com/drive/v3/files/${archivo.id}?alt=media", token)
                try {
                    verificar(con)
                    con.inputStream.use { entrada -> destino.outputStream().use { entrada.copyTo(it) } }
                } finally {
                    con.disconnect()
                }
                destino.inputStream().use { Respaldo.restaurarDesde(ctx, db, it) }
            } finally {
                destino.delete()
            }
        }

    private fun subirArchivo(zip: File, token: String) {
        val limite = "agenda${System.nanoTime()}"
        val metadatos = JSONObject()
            .put("name", NOMBRE_ARCHIVO)
            .put("parents", org.json.JSONArray().put("appDataFolder"))
            .toString()
        val cabecera = (
            "--$limite\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                metadatos + "\r\n" +
                "--$limite\r\n" +
                "Content-Type: application/zip\r\n\r\n"
            ).toByteArray(Charsets.UTF_8)
        val pie = "\r\n--$limite--\r\n".toByteArray(Charsets.UTF_8)

        val con = abrir("POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id", token)
        try {
            con.doOutput = true
            con.setRequestProperty("Content-Type", "multipart/related; boundary=$limite")
            con.setFixedLengthStreamingMode(cabecera.size + zip.length() + pie.size)
            con.outputStream.use { salida ->
                salida.write(cabecera)
                zip.inputStream().use { it.copyTo(salida) }
                salida.write(pie)
            }
            verificar(con)
            con.inputStream.close()
        } finally {
            con.disconnect()
        }
    }

    private fun abrir(metodo: String, url: String, token: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = metodo
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
        }

    private fun verificar(con: HttpURLConnection) {
        val codigo = con.responseCode
        if (codigo !in 200..299) {
            val detalle = con.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw DriveException(codigo, "Drive respondió $codigo: ${detalle.take(300)}")
        }
    }

    private fun pedir(metodo: String, url: String, token: String): String {
        val con = abrir(metodo, url, token)
        try {
            verificar(con)
            return con.inputStream.bufferedReader().use { it.readText() }
        } finally {
            con.disconnect()
        }
    }
}
