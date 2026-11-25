# AgendaTurnos

App Android para llevar la agenda de turnos de una profesional de podología, auriculoterapia y manicura, desde la perspectiva de quien atiende.

## Funcionalidades (en desarrollo)

- Clientes: alta, edición, búsqueda sin acentos, perfil con historial resumido.
- Catálogo de tratamientos con precio base editable.
- Turnos con uno o varios tratamientos, a domicilio o en consultorio.
- Turnos por cerrar, cancelación con motivo, reprogramación.
- Próximamente: cierre con fotos antes/después, historial, avisos (5 h y 1 h 30 antes) y recontacto a los 20 días.

## Stack

- Kotlin + Jetpack Compose (Material 3)
- Room (SQLite local) con KSP
- Navigation Compose
- minSdk 26

## Cómo correrla

1. Abrir el proyecto en Android Studio.
2. Sync de Gradle.
3. Run ▶ en un dispositivo o emulador.

En la versión de desarrollo, el menú ⋮ de Inicio tiene "Cargar datos de prueba".
