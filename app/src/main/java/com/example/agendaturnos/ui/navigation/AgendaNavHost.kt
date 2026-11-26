package com.example.agendaturnos.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.agendaturnos.data.AgendaRepository
import com.example.agendaturnos.lector.MensajeCompartido
import com.example.agendaturnos.data.model.EstadoTurno
import com.example.agendaturnos.ui.screens.AgendarScreen
import com.example.agendaturnos.ui.screens.BuscarClientesScreen
import com.example.agendaturnos.ui.screens.CerrarTurnoScreen
import com.example.agendaturnos.ui.screens.DesdeMensajeScreen
import com.example.agendaturnos.ui.screens.DetalleTratamientoScreen
import com.example.agendaturnos.ui.screens.HistorialScreen
import com.example.agendaturnos.ui.screens.EditarTurnoScreen
import com.example.agendaturnos.ui.screens.NuevoClienteTurnoScreen
import com.example.agendaturnos.ui.screens.TurnoDetalleScreen
import com.example.agendaturnos.ui.screens.CatalogoScreen
import com.example.agendaturnos.ui.screens.ClienteFormScreen
import com.example.agendaturnos.ui.screens.InicioScreen
import com.example.agendaturnos.ui.screens.PerfilClienteScreen
import com.example.agendaturnos.ui.screens.RespaldoScreen

@Composable
fun AgendaNavHost(
    repo: AgendaRepository,
    turnoDesdeAviso: Long? = null,
    onAvisoAbierto: () -> Unit = {},
    mensajeCompartido: String? = null,
    onMensajeAbierto: () -> Unit = {},
) {
    val nav = rememberNavController()
    val volver: () -> Unit = { nav.popBackStack() }
    val argTurno = listOf(navArgument("turnoId") { type = NavType.LongType })
    val argCliente = listOf(navArgument("clienteId") { type = NavType.LongType })

    // Al tocar la notificación de un turno, se abre su agenda completa.
    // Texto compartido desde WhatsApp: se abre el lector de mensajes.
    LaunchedEffect(mensajeCompartido) {
        mensajeCompartido?.let {
            MensajeCompartido.poner(it)
            nav.navigate(Rutas.DESDE_MENSAJE)
            onMensajeAbierto()
        }
    }

    LaunchedEffect(turnoDesdeAviso) {
        turnoDesdeAviso?.let {
            nav.navigate(Rutas.turno(it))
            onAvisoAbierto()
        }
    }

    NavHost(navController = nav, startDestination = Rutas.INICIO) {
        composable(Rutas.INICIO) {
            InicioScreen(
                repo = repo,
                onAbrirTurno = { nav.navigate(Rutas.turno(it)) },
                onCerrarTurno = { nav.navigate(Rutas.cerrarTurno(it)) },
                onNavegar = { nav.navigate(it) },
            )
        }
        composable(Rutas.BUSCAR) {
            BuscarClientesScreen(
                repo = repo,
                onVolver = volver,
                onAbrirCliente = { nav.navigate(Rutas.cliente(it)) },
                onNuevoCliente = { nav.navigate(Rutas.clienteForm(0)) },
            )
        }
        composable(Rutas.NUEVO_CLIENTE) {
            NuevoClienteTurnoScreen(
                repo = repo,
                onVolver = volver,
                onCreado = { turnoId ->
                    nav.navigate(Rutas.turno(turnoId)) {
                        popUpTo(Rutas.NUEVO_CLIENTE) { inclusive = true }
                    }
                },
            )
        }
        composable(
            Rutas.AGENDAR,
            listOf(navArgument("clienteId") { type = NavType.LongType; defaultValue = 0L }),
        ) { entry ->
            AgendarScreen(
                repo = repo,
                clienteIdInicial = entry.arguments?.getLong("clienteId") ?: 0L,
                onVolver = volver,
                onCreado = { turnoId ->
                    nav.navigate(Rutas.turno(turnoId)) {
                        popUpTo(Rutas.AGENDAR) { inclusive = true }
                    }
                },
            )
        }
        composable(Rutas.CATALOGO) { CatalogoScreen(repo = repo, onVolver = volver) }
        composable(Rutas.RESPALDO) { RespaldoScreen(onVolver = volver) }
        composable(Rutas.DESDE_MENSAJE) {
            DesdeMensajeScreen(
                repo = repo,
                onVolver = volver,
                onAgendarExistente = { id ->
                    nav.navigate(Rutas.agendar(id)) { popUpTo(Rutas.DESDE_MENSAJE) { inclusive = true } }
                },
                onNuevoCliente = {
                    nav.navigate(Rutas.NUEVO_CLIENTE) { popUpTo(Rutas.DESDE_MENSAJE) { inclusive = true } }
                },
            )
        }
        composable(Rutas.TURNO, argTurno) { entry ->
            val id = entry.arguments?.getLong("turnoId") ?: 0L
            TurnoDetalleScreen(
                repo = repo,
                turnoId = id,
                onVolver = volver,
                onAbrirCliente = { nav.navigate(Rutas.cliente(it)) },
                onEditar = { nav.navigate(Rutas.editarTurno(id)) },
                onTerminar = { nav.navigate(Rutas.cerrarTurno(id)) },
                onVerDetalle = { nav.navigate(Rutas.detalleTratamiento(id)) },
            )
        }
        composable(Rutas.EDITAR_TURNO, argTurno) { entry ->
            EditarTurnoScreen(
                repo = repo,
                turnoId = entry.arguments?.getLong("turnoId") ?: 0L,
                onVolver = volver,
                onGuardado = volver,
            )
        }
        composable(Rutas.CERRAR_TURNO, argTurno) { entry ->
            val id = entry.arguments?.getLong("turnoId") ?: 0L
            CerrarTurnoScreen(
                repo = repo,
                turnoId = id,
                onVolver = volver,
                onListo = { nav.popBackStack(Rutas.INICIO, inclusive = false) },
                onReprogramar = {
                    nav.navigate(Rutas.editarTurno(id)) {
                        popUpTo(Rutas.CERRAR_TURNO) { inclusive = true }
                    }
                },
            )
        }
        composable(Rutas.DETALLE_TRATAMIENTO, argTurno) { entry ->
            DetalleTratamientoScreen(
                repo = repo,
                turnoId = entry.arguments?.getLong("turnoId") ?: 0L,
                onVolver = volver,
                onAbrirCliente = { nav.navigate(Rutas.cliente(it)) },
            )
        }
        composable(Rutas.CLIENTE, argCliente) { entry ->
            val id = entry.arguments?.getLong("clienteId") ?: 0L
            PerfilClienteScreen(
                repo = repo,
                clienteId = id,
                onVolver = volver,
                onEditar = { nav.navigate(Rutas.clienteForm(id)) },
                onHistorial = { nav.navigate(Rutas.historial(id)) },
                onAgendar = { nav.navigate(Rutas.agendar(id)) },
                onAbrirTurno = { nav.navigate(Rutas.turno(it)) },
            )
        }
        composable(Rutas.CLIENTE_FORM, argCliente) { entry ->
            val id = entry.arguments?.getLong("clienteId") ?: 0L
            ClienteFormScreen(
                repo = repo,
                clienteId = id,
                onVolver = volver,
                onGuardado = { guardadoId ->
                    if (id == 0L) {
                        // Cliente nuevo: ir a su perfil sin dejar el formulario atrás.
                        nav.navigate(Rutas.cliente(guardadoId)) {
                            popUpTo(Rutas.CLIENTE_FORM) { inclusive = true }
                        }
                    } else {
                        nav.popBackStack()
                    }
                },
                onVerCliente = { existenteId ->
                    nav.navigate(Rutas.cliente(existenteId)) {
                        popUpTo(Rutas.CLIENTE_FORM) { inclusive = true }
                    }
                },
                onArchivado = {
                    if (!nav.popBackStack(Rutas.BUSCAR, inclusive = false)) {
                        nav.popBackStack(Rutas.INICIO, inclusive = false)
                    }
                },
            )
        }
        composable(Rutas.HISTORIAL, argCliente) { entry ->
            HistorialScreen(
                repo = repo,
                clienteId = entry.arguments?.getLong("clienteId") ?: 0L,
                onVolver = volver,
                onAbrir = { t ->
                    if (t.turno.estado == EstadoTurno.TERMINADO) {
                        nav.navigate(Rutas.detalleTratamiento(t.turno.id))
                    } else {
                        nav.navigate(Rutas.turno(t.turno.id))
                    }
                },
            )
        }
    }
}
