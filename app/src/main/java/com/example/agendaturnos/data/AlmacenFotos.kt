package com.example.agendaturnos.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Manejo de las fotos de antes y después.
 * Se guardan comprimidas en el almacenamiento interno de la app
 * (no aparecen en la galería del teléfono).
 */
object AlmacenFotos {
    private const val LADO_MAXIMO = 1600
    private const val CALIDAD_JPEG = 80

    private fun pendientes(ctx: Context) = File(ctx.cacheDir, "fotos_pendientes").apply { mkdirs() }
    private fun definitivas(ctx: Context) = File(ctx.filesDir, "fotos").apply { mkdirs() }

    /** Archivo vacío donde la cámara va a escribir la foto. */
    fun nuevoArchivoCamara(ctx: Context): File =
        File(pendientes(ctx), "camara_${System.currentTimeMillis()}.jpg")

    fun uriPara(ctx: Context, archivo: File): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fotos", archivo)

    /** Reduce, endereza y comprime la imagen. Devuelve un archivo pendiente de confirmar. */
    suspend fun comprimir(ctx: Context, origen: Uri): File? = withContext(Dispatchers.IO) {
        val bitmap = decodificar({ ctx.contentResolver.openInputStream(origen) }, LADO_MAXIMO)
            ?: return@withContext null
        val destino = File(pendientes(ctx), "foto_${System.nanoTime()}.jpg")
        destino.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, it) }
        bitmap.recycle()
        destino
    }

    /** Pasa una foto pendiente a la carpeta definitiva. Devuelve la ruta a guardar en la base. */
    fun confirmar(ctx: Context, pendiente: File, nombre: String): String {
        val destino = File(definitivas(ctx), "$nombre.jpg")
        if (!pendiente.renameTo(destino)) {
            pendiente.copyTo(destino, overwrite = true)
            pendiente.delete()
        }
        return destino.absolutePath
    }

    /** Carga una foto guardada, reducida a [ladoMaximo] píxeles, para mostrarla. */
    suspend fun cargar(ruta: String, ladoMaximo: Int): Bitmap? = withContext(Dispatchers.IO) {
        val archivo = File(ruta)
        if (!archivo.exists()) null else decodificar({ archivo.inputStream() }, ladoMaximo)
    }

    private fun decodificar(abrir: () -> InputStream?, ladoMaximo: Int): Bitmap? {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        abrir()?.use { BitmapFactory.decodeStream(it, null, limites) }
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null

        var muestra = 1
        while (limites.outWidth / (muestra * 2) >= ladoMaximo || limites.outHeight / (muestra * 2) >= ladoMaximo) {
            muestra *= 2
        }
        var bitmap = abrir()?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestra })
        } ?: return null

        val mayor = maxOf(bitmap.width, bitmap.height)
        if (mayor > ladoMaximo) {
            val escala = ladoMaximo.toFloat() / mayor
            val reducido = Bitmap.createScaledBitmap(
                bitmap, (bitmap.width * escala).toInt(), (bitmap.height * escala).toInt(), true,
            )
            if (reducido != bitmap) bitmap.recycle()
            bitmap = reducido
        }

        // Las fotos de la cámara suelen venir "acostadas": se enderezan según el EXIF.
        val orientacion = try {
            abrir()?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (e: Exception) {
            null
        } ?: ExifInterface.ORIENTATION_NORMAL
        val grados = when (orientacion) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (grados != 0f) {
            val rotado = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(grados) }, true,
            )
            if (rotado != bitmap) bitmap.recycle()
            bitmap = rotado
        }
        return bitmap
    }
}
