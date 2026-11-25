package com.example.agendaturnos.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.agendaturnos.data.AgendaRepository
import com.example.agendaturnos.ui.screens.AgendarScreen
import com.example.agendaturnos.ui.screens.BuscarClientesScreen
import com.example.agendaturnos.ui.screens.EditarTurnoScreen
import com.example.agendaturnos.ui.screens.NuevoClienteTurnoScreen
import com.example.agendaturnos.ui.screens.TurnoDetalleScreen
import com.example.agendaturnos.ui.screens.CatalogoScreen
import com.example.agendaturnos.ui.screens.ClienteFormScreen
import com.example.agendaturnos.ui.screens.InicioScreen
import com.example.agendaturnos.ui.screens.PerfilClienteScreen
import com.example.agendaturnos.ui.screens.PantallaPendiente

@Composable
fun AgendaNavHost(repo: AgendaRepository) {
    val nav = rememberNavController()
    val volver: () -> Unit = { nav.popBackStack() }
    val argTurno = listOf(navArgument("turnoId") { type = NavType.LongType })
    val argCliente = listOf(navArgument("clienteId") { type = NavType.LongType })

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
        composable(Rutas.CERRAR_TURNO, argTurno) { PantallaPendiente("Cerrar turno", "Etapa 5", volver) }
        composable(Rutas.DETALLE_TRATAMIENTO, argTurno) { PantallaPendiente("Detalle del tratamiento", "Etapa 5", volver) }
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
        composable(Rutas.HISTORIAL, argCliente) { PantallaPendiente("Historial", "Etapa 5", volver) }
    }
}
