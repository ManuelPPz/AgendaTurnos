package com.example.agendaturnos

import android.app.Application
import com.example.agendaturnos.data.AgendaRepository
import com.example.agendaturnos.data.db.AgendaDatabase

class AgendaApp : Application() {
    val database: AgendaDatabase by lazy { AgendaDatabase.crear(this) }
    val repository: AgendaRepository by lazy { AgendaRepository(database) }
}
