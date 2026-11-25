package com.example.agendaturnos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.agendaturnos.ui.navigation.AgendaNavHost
import com.example.agendaturnos.ui.theme.AgendaTurnosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as AgendaApp).repository
        setContent {
            AgendaTurnosTheme {
                AgendaNavHost(repo)
            }
        }
    }
}
