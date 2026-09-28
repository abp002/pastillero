# Pastillero

App Android («Mis pastillas») que recuerda sus pastillas a una persona mayor e insiste hasta
que confirma la toma. Avisa a un familiar por ntfy si no la confirma. Issue: ALE-236.
El repo es **público** (github.com/abp002/pastillero): nada de datos de salud reales ni de quién
la usa en código, docs ni commits. Eso vive en Linear y en el vault.
Los criterios de aceptación acordados están en ALE-236 y cada uno tiene su test.

## QA
Nivel: produccion

Bitácora: ALE

Contrato: `.claude/code/run.sh <verbo>` (tipos, humo, rapido, test, cov, sec; e2e y mutacion sin implementar).
- `tipos`: compila todo y pasa el lint de Android con `warningsAsErrors` (lint limpio es parte de «hecho»).
- `humo`: Robolectric abre `MainActivity` y `AlarmaActivity` con una toma sonando.
- `cov`: Kover → `build/lcov.info` (conversor en `.claude/code/jacoco-a-lcov.py`).

## Estructura
- `:nucleo` — Kotlin puro, sin Android, Java 17. Toda la lógica:
  - `Planificador.planificar(ahora, meds, tomas, ajustes, zona)`: función pura. Devuelve las tomas
    que cambian, las acciones (sonar, retirar, avisar al hijo) y la **única** próxima revisión.
  - `Calendario`: cuándo toca la siguiente (horas fijas = hora de reloj; intervalo = desde la
    toma real) y el **cierre a mitad de camino** hasta la siguiente (evita la doble dosis).
  - `Operaciones`: tomada, silenciar, posponer, adelantar. Todas idempotentes.
  - Tests: criterios con un `Simulador` que solo se despierta cuando saltaría la alarma, más un
    test de propiedades (300 semillas).
  - Solo APIs de `java.time` que existen en Android 26 (nada de `toMinutesPart()` y similares).
- `:app` — Android (Compose, Room, WorkManager). `Revisor` lo cablea todo bajo un `Mutex`.
  - Una sola alarma: `Despertador` con `setAlarmClock` y siempre el mismo `PendingIntent`.
  - Cada notificación de toma lleva la etiqueta `toma-<med>-<ms>` y sus botones llevan la URI
    `pastillero://toma/<med>/<ms>`. Sin la URI, dos tomas a la misma hora comparten el
    `PendingIntent` y se pisan los extras.
  - Tests de integración con Robolectric (SDK 36) y la BD en memoria.

## Comandos
```bash
./gradlew :nucleo:test :app:testDebugUnitTest   # suite (~6 s en caliente)
./gradlew :app:lintDebug                        # lint (avisos = errores)
./gradlew :app:assembleRelease                  # APK firmado: app/build/outputs/apk/release/
```
SDK en `~/Library/Android/sdk` (con `cmdline-tools;latest` dentro). JDK 25 (Temurin).
Robolectric en JDK 25 necesita los `--add-exports/--add-opens` que hay en `app/build.gradle.kts`.

## Firma
La clave está en `~/.android/pastillero-release.jks` y sus contraseñas en `~/.gradle/gradle.properties`
(`PASTILLERO_*`), fuera del repo. **Si se pierde, no se puede actualizar la app en el móvil donde está
instalada sin desinstalarla, y al desinstalarla se pierde el historial.** Hay que tener copia del `.jks` y
de la contraseña.

## Publicar una versión
`scripts/publicar.sh X.Y.Z "qué cambia"`: exige árbol limpio, rama main, `gh` en abp002, `tipos` y
`test` en verde. Después sube `pastilleroVersion` en `gradle.properties` (el versionCode se calcula
de ahí), compila el APK firmado, comprueba firma y versión, hace commit, tag y push, y crea la release
en GitHub con el APK. En el móvil, la propia app se actualiza sola (ALE-243): `ActualizacionWorker`
mira cada 6 h la última release (`ClienteGithub`) y la instala con `PackageInstaller`. Sin preguntar en
Android 12+ (autoactualización con `UPDATE_PACKAGES_WITHOUT_USER_ACTION`); antes, con notificación.
No instala con una toma sonando ni a menos de 15 min de la siguiente, porque actualizar reinicia la app.
Tras actualizar, `MY_PACKAGE_REPLACED` reprograma la alarma. «Buscar actualización ahora» está en Ajustes.
Si un cambio toca la BD (entidades de Room), hay que subir la versión de `BaseDeDatos` y escribir la
migración: el esquema exportado está en `app/schemas/`. Sin migración, la actualización rompe la app o
borra el historial.

## Emulador (pasada manual)
AVD `pastillero` (Android 16, arm64). Arranque sin ventana:
`$ANDROID_HOME/emulator/emulator -avd pastillero -no-window -no-audio -timezone Europe/Madrid`.
Para no esperar a las alarmas: `adb root`, `settings put global auto_time 0` y `date MMDDhhmmYYYY.ss`.
El cambio de hora también prueba el receptor `TIME_SET`.

## Trampas de Android que ya han mordido
- **Pantalla completa en Android 16 (ALE-239)**: con el appop `USE_FULL_SCREEN_INTENT` en modo
  `default`, `canUseFullScreenIntent()` dice que sí y Ajustes enseña el interruptor activado, pero al
  entregar la alarma el sistema la rechaza. Solo vale `MODE_ALLOWED`. El interruptor de Ajustes fija el
  **modo por uid**. `Permisos` lo comprueba con `unsafeCheckOpNoThrow`.
- Los canales de notificación no se pueden cambiar una vez creados: para cambiar el sonido, canal
  nuevo (`alarma_v2`…).
- Tras renombrar una carpeta de `res/`, AGP puede quedarse con el estado incremental viejo:
  `--rerun-tasks` en `processDebugResources`.
