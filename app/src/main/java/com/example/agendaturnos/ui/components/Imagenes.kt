package com.example.agendaturnos.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agendaturnos.data.AlmacenFotos

/** Muestra una foto guardada en el teléfono. */
@Composable
fun ImagenArchivo(
    ruta: String,
    descripcion: String,
    modifier: Modifier = Modifier,
    ladoMaximo: Int = 800,
    escala: ContentScale = ContentScale.Crop,
) {
    val imagen by produceState<ImageBitmap?>(initialValue = null, ruta, ladoMaximo) {
        value = AlmacenFotos.cargar(ruta, ladoMaximo)?.asImageBitmap()
    }
    val img = imagen
    if (img != null) {
        Image(bitmap = img, contentDescription = descripcion, modifier = modifier, contentScale = escala)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

/** Foto a pantalla completa. Se cierra tocándola. */
@Composable
fun VisorFoto(ruta: String, descripcion: String, onCerrar: () -> Unit) {
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onCerrar),
            contentAlignment = Alignment.Center,
        ) {
            ImagenArchivo(
                ruta = ruta,
                descripcion = descripcion,
                modifier = Modifier.fillMaxSize(),
                ladoMaximo = 2000,
                escala = ContentScale.Fit,
            )
            Text(
                descripcion,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}
