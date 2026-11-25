# AgendaTurnos

App Android para llevar la agenda de turnos de una profesional de podología, auriculoterapia y manicura, desde la perspectiva de quien atiende.

## Funcionalidades

- Clientes: alta, edición, archivo, búsqueda sin acentos y perfil con resumen de visitas.
- Catálogo de tratamientos con precio base editable.
- Turnos con uno o varios tratamientos, a domicilio o en consultorio; editar, reprogramar y cancelar.
- Inicio con turnos por cerrar, próximos turnos agrupados (Hoy, Mañana, Esta semana, Más adelante) y clientes para recontactar.
- Cierre de turno con precio final, descripción y fotos de antes y después.
- Historial por cliente y detalle de cada tratamiento con fotos.
- Avisos 5 h y 1 h 30 antes de cada turno; aviso diario de clientes con 20 días sin turno (WhatsApp prearmado).
- Respaldo completo (base de datos + fotos) en un .zip, y restauración.

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

## Créditos

- Ícono: la palabra "Agenda" dibujada con la tipografía Dancing Script (Impallari Type, licencia SIL Open Font License 1.1).
