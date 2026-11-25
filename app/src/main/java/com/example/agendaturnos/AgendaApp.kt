package com.example.agendaturnos

import android.app.Application
import com.example.agendaturnos.avisos.Notificaciones
import com.example.agendaturnos.avisos.ProgramadorAvisos
import com.example.agendaturnos.data.AgendaRepository
import com.example.agendaturnos.data.db.AgendaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AgendaApp : Application() {
    val database: AgendaDatabase by lazy { AgendaDatabase.crear(this) }
    val programador: ProgramadorAvisos by lazy { ProgramadorAvisos(this) }
    val repository: AgendaRepository by lazy { AgendaRepository(database, programador) }

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notificaciones.crearCanales(this)
        programador.programarRecontactoDiario()
        // Por las dudas, al abrir la app se reprograman los avisos de los turnos futuros.
        alcance.launch { repository.reprogramarTodosLosAvisos() }
    }
}
