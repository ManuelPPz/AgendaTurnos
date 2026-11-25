package com.example.agendaturnos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.agendaturnos.avisos.Notificaciones
import com.example.agendaturnos.ui.navigation.AgendaNavHost
import com.example.agendaturnos.ui.theme.AgendaTurnosTheme

class MainActivity : ComponentActivity() {
    /** Turno a abrir cuando se toca una notificación. */
    private var turnoDesdeAviso by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) turnoDesdeAviso = intent.turnoDeAviso()
        val repo = (application as AgendaApp).repository
        setContent {
            AgendaTurnosTheme {
                AgendaNavHost(
                    repo = repo,
                    turnoDesdeAviso = turnoDesdeAviso,
                    onAvisoAbierto = { turnoDesdeAviso = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.turnoDeAviso()?.let { turnoDesdeAviso = it }
    }

    private fun Intent.turnoDeAviso(): Long? =
        getLongExtra(Notificaciones.EXTRA_TURNO, 0L).takeIf { it > 0L }
}
