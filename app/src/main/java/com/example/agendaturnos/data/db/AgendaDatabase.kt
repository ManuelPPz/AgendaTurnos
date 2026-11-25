package com.example.agendaturnos.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.agendaturnos.data.model.Aviso
import com.example.agendaturnos.data.model.Cliente
import com.example.agendaturnos.data.model.Foto
import com.example.agendaturnos.data.model.TratamientoCatalogo
import com.example.agendaturnos.data.model.Turno
import com.example.agendaturnos.data.model.TurnoTratamiento

@Database(
    entities = [
        Cliente::class,
        TratamientoCatalogo::class,
        Turno::class,
        TurnoTratamiento::class,
        Foto::class,
        Aviso::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AgendaDatabase : RoomDatabase() {
    abstract fun clienteDao(): ClienteDao
    abstract fun tratamientoDao(): TratamientoDao
    abstract fun turnoDao(): TurnoDao
    abstract fun fotoDao(): FotoDao
    abstract fun avisoDao(): AvisoDao

    companion object {
        /** Tratamientos con los que arranca la app. Precio 0 = a determinar. */
        private val TRATAMIENTOS_INICIALES = listOf("Podología", "Auriculoterapia", "Manicura")

        fun crear(context: Context): AgendaDatabase =
            Room.databaseBuilder(context, AgendaDatabase::class.java, "agenda.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        TRATAMIENTOS_INICIALES.forEach { nombre ->
                            db.execSQL(
                                "INSERT INTO tratamientos (nombre, precioBase, duracionMin, activo) VALUES (?, 0, NULL, 1)",
                                arrayOf(nombre),
                            )
                        }
                    }
                })
                .build()
    }
}
