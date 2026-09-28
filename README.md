# Pastillero

Recordatorio de pastillas para Android pensado para una persona mayor: **insiste hasta que
confirma la toma** y, si no lo hace, **avisa a un familiar** por [ntfy](https://ntfy.sh).

- Avisa a su hora y repite cada pocos minutos. Si sigue sin marcarla, pasa a una **alarma a
  pantalla completa** que suena aunque el móvil esté en silencio y bloqueado.
- **«Ya me la he tomado» antes de hora**, sin que después vuelva a sonar: nada de dobles dosis.
- Apunta la **hora real** de cada toma: a tiempo, tarde o adelantada, con un margen por pastilla.
- Pautas **a horas fijas** (un retraso no mueve la siguiente) o **cada X horas** desde la toma real.
- Si pasa un rato sin marcarla, llega un aviso al móvil del familiar, y otro cuando por fin la marca.
- Sigue funcionando tras reiniciar el móvil, con el cambio de hora y con el ahorro de batería. Si falta un
  permiso, la pantalla principal dice cuál y lleva al ajuste.
- Letra grande, una pantalla, ajustes tras PIN. Todo se queda en el móvil: sin cuentas ni servidores.
- **Se actualiza sola** desde las releases de este repo, sin que quien la usa tenga que hacer nada.

## Instalar

Necesita Android 8 o superior.

Descarga el APK de la [última versión](https://github.com/abp002/pastillero/releases/latest) e instálalo.
Desde ese momento la app mira cada pocas horas si hay una versión nueva y la instala ella misma.
Con Android 12 o superior no pregunta nada; en versiones anteriores deja una notificación para
tocar «Actualizar». Android solo acepta actualizaciones firmadas con la misma clave.

Al abrirla por primera vez, sigue el recuadro rojo hasta que desaparezca (incluido el permiso de
«instalar actualizaciones»). En Android 14 o superior,
si el ajuste de «pantalla completa» ya sale activado, apágalo y vuelve a encenderlo: en algunos
móviles el valor por defecto se ve activado pero no funciona.

## Cómo está hecho

- `nucleo/` es Kotlin puro. Toda la decisión de qué suena y cuándo es una función pura
  (`Planificador`). Se prueba con un simulador que solo se despierta cuando saltaría la alarma y con
  tests de propiedades.
- `app/` es Android con Jetpack Compose, Room y WorkManager. Hay **una sola alarma** (`setAlarmClock`),
  que se recalcula tras cada pulsación, reinicio o cambio de hora. Así nunca queda una alarma vieja
  que suene después de haberla tomado.

```bash
./gradlew :nucleo:test :app:testDebugUnitTest   # tests
./gradlew :app:lintDebug                        # lint (los avisos cuentan como errores)
```
