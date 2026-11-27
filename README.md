# AgendaTurnos

App Android para llevar la agenda de turnos de una profesional de **podología, auriculoterapia y manicura**, pensada desde la perspectiva de quien atiende: clientes, turnos a domicilio o en consultorio, cierre con fotos, avisos y respaldo en Google Drive.

Funciona sin internet; solo usa la red para el respaldo en Drive y para abrir WhatsApp o mapas.

## Funcionalidades

**Clientes**
- Alta, edición y archivo (no se borran: se conserva el historial).
- Búsqueda por nombre o teléfono, sin importar acentos ni mayúsculas.
- Perfil con edad, preferencias, cantidad de visitas, última visita y próximo turno.
- Botones para llamar, abrir WhatsApp (formato uruguayo +598) y ver la dirección en el mapa.
- Aviso de posibles duplicados al cargar un cliente.

**Tratamientos**
- Catálogo editable (arranca con Podología, Auriculoterapia y Manicura).
- Precio base y duración estimada; se pueden desactivar sin perder el historial.

**Turnos**
- Uno o varios tratamientos por turno, cada uno con su precio (editable).
- A domicilio (con dirección) o en consultorio.
- "Repetir último" para agendar lo mismo que la vez anterior.
- Editar, reprogramar o cancelar con motivo; aviso si ya hay otro turno a la misma hora.

**Inicio**
- Turnos por cerrar (fecha pasada) con botón **Terminar**.
- Próximos turnos agrupados en Hoy, Mañana, Esta semana y Más adelante.
- Clientes para recontactar: 20 días sin turno, con mensaje de WhatsApp prearmado, agendar, posponer o descartar.

**Cierre e historial**
- Cerrar como terminado (precio final, qué se hizo, foto de antes y de después) o cancelado.
- Fotos desde cámara o galería, comprimidas y guardadas dentro de la app.
- Historial por cliente con total cobrado y detalle de cada tratamiento con fotos ampliables.

**Avisos**
- Notificación 5 h y 1 h 30 antes de cada turno (al tocarla se abre el turno).
- Aviso diario de clientes para recontactar.
- Se reprograman solos al agendar, editar, cancelar, reiniciar el teléfono o cambiar la hora.

**Respaldo**
- Automático en **Google Drive** (carpeta privada de la app): una vez por día y después de cerrar cada turno. Se guardan las últimas 3 versiones.
- En un celular nuevo: instalar, conectar la misma cuenta de Google y restaurar.
- Respaldo manual en un archivo `.zip` (base de datos + fotos) y restauración desde archivo.

## Stack

| Parte | Tecnología |
| --- | --- |
| Lenguaje e interfaz | Kotlin, Jetpack Compose, Material 3 |
| Base de datos | Room (SQLite local) con KSP |
| Navegación | Navigation Compose (transición deslizante) |
| Avisos | AlarmManager (alarmas exactas) y notificaciones |
| Tareas en segundo plano | WorkManager |
| Google Drive | play-services-auth (AuthorizationClient) + API REST de Drive v3 |
| Versión mínima | Android 8.0 (API 26) |

## Estructura del código

```
app/src/main/java/com/example/agendaturnos/
├── AgendaApp.kt            Inicio de la app: base, avisos y respaldo diario
├── MainActivity.kt         Actividad única; abre el turno al tocar una notificación
├── avisos/                 Notificaciones, alarmas y receptores (arranque, hora)
├── data/                   Repositorio, fotos y respaldo en .zip
│   ├── db/                 Room: base, DAOs y conversores de fechas
│   └── model/              Tablas (clientes, tratamientos, turnos, fotos, avisos)
├── drive/                  Respaldo en Google Drive y tarea diaria
└── ui/
    ├── components/         Editor de turno, selectores de fecha/hora, imágenes
    ├── navigation/         Rutas y NavHost
    ├── screens/            Pantallas
    └── theme/              Colores y tipografía
```

## Cómo correrla

1. Abrir el proyecto en Android Studio y hacer **Sync**.
2. Elegir la variante en **Build → Select Build Variant**:
   - `debug`: para desarrollar.
   - `release`: la versión real (más fluida). Está firmada con la clave de desarrollo para poder instalarla desde Android Studio sin perder datos.
3. Run ▶ en el teléfono (con depuración USB) o en un emulador.

Para instalar sin cable: **Build → Generate APKs** y pasar el `.apk` al teléfono.

Las primeras veces la app pide permiso de **notificaciones** y, en Android 12+, de **alarmas exactas** (desde la tarjeta en Inicio) para que los avisos lleguen a horario.

## Configurar Google Drive

El respaldo en Drive necesita un proyecto en [Google Cloud Console](https://console.cloud.google.com):

1. Habilitar **Google Drive API**.
2. En **Google Auth Platform**: público **Externo**, permiso `.../auth/drive.appdata` y la cuenta a usar como **usuario de prueba**.
3. Crear un cliente OAuth de tipo **Android** con el paquete `com.example.agendaturnos` y la huella **SHA-1** de la clave con la que se firma la app:

```bash
"/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool" -exportcert \
  -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -rfc \
  | openssl x509 -noout -fingerprint -sha1
```

Si se firma con otra clave (por ejemplo, para publicar), hay que agregar su SHA-1 como otro cliente Android.

## Ramas

- `main`: versión estable.
- `feature/...` y `fix/...`: trabajo en curso; se integran a `main` con Pull Request.

## Créditos

- Ícono: la palabra "Agenda" dibujada con la tipografía Dancing Script (Impallari Type, licencia SIL Open Font License 1.1).
