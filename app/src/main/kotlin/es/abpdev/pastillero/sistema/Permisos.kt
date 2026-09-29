package es.abpdev.pastillero.sistema

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri

enum class Permiso(val titulo: String, val porQue: String) {
    NOTIFICACIONES("Notificaciones", "Sin ellas no puede avisarle."),
    ALARMAS_EXACTAS("Alarmas exactas", "Para que suene a su hora y no minutos después."),
    PANTALLA_COMPLETA(
        "Pantalla completa",
        "Para que la alarma se vea con el móvil bloqueado. Si ya sale activado, apágalo y vuelve a encenderlo.",
    ),
    SUPERPONER(
        "Mostrar sobre otras apps",
        "Para que la pastilla salga en grande aunque esté usando el móvil, y no solo como una notificación.",
    ),
    BATERIA("Batería sin restricciones", "Para que el ahorro de batería no la duerma."),
    ACTUALIZAR("Instalar actualizaciones", "Para que se actualice sola, sin que tengas que hacer nada."),
}

/**
 * Lo que tiene que estar concedido para que los avisos lleguen (criterio 8).
 *
 * @param sistemaDicePantallaCompleta lo que contesta `canUseFullScreenIntent()`; aparte porque
 *   Robolectric no lo emula.
 */
class Permisos(
    private val context: Context,
    private val sistemaDicePantallaCompleta: () -> Boolean = {
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    },
) {

    fun faltan(): List<Permiso> = buildList {
        if (!notificacionesActivas()) add(Permiso.NOTIFICACIONES)
        if (Build.VERSION.SDK_INT >= 31 && !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            add(Permiso.ALARMAS_EXACTAS)
        }
        if (Build.VERSION.SDK_INT >= 34 && !pantallaCompletaConcedida()) add(Permiso.PANTALLA_COMPLETA)
        if (!Settings.canDrawOverlays(context)) add(Permiso.SUPERPONER)
        if (!context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)) {
            add(Permiso.BATERIA)
        }
        if (!context.packageManager.canRequestPackageInstalls()) add(Permiso.ACTUALIZAR)
    }

    /**
     * El ajuste del sistema que lo arregla. Las constantes de Android 12 y 14 solo se usan
     * cuando [faltan] las ha pedido, y eso solo pasa en esas versiones.
     */
    @SuppressLint("BatteryLife", "InlinedApi") // BatteryLife: se instala a mano, no por Play.
    fun ajuste(permiso: Permiso): Intent {
        val paquete = "package:${context.packageName}".toUri()
        val intent = when (permiso) {
            Permiso.NOTIFICACIONES ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            Permiso.ALARMAS_EXACTAS -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, paquete)
            Permiso.PANTALLA_COMPLETA -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, paquete)
            Permiso.SUPERPONER -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, paquete)
            Permiso.BATERIA -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, paquete)
            Permiso.ACTUALIZAR -> Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, paquete)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * En Android 16 el modo «por defecto» del permiso engaña: `canUseFullScreenIntent()` dice que
     * sí y Ajustes lo enseña activado, pero al entregar la alarma el sistema lo rechaza (ALE-239).
     * Solo vale el «permitido» explícito, que se fija al tocar el interruptor.
     */
    @RequiresApi(34)
    private fun pantallaCompletaConcedida(): Boolean {
        if (!sistemaDicePantallaCompleta()) return false
        val modo = try {
            context.getSystemService(AppOpsManager::class.java)
                .unsafeCheckOpNoThrow(OP_PANTALLA_COMPLETA, context.applicationInfo.uid, context.packageName)
        } catch (_: IllegalArgumentException) {
            return true // un sistema que no conoce la operación: vale lo que diga canUseFullScreenIntent()
        }
        return modo == AppOpsManager.MODE_ALLOWED
    }

    /** Un canal apagado a mano tampoco avisa, aunque la app tenga permiso. */
    private fun notificacionesActivas(): Boolean {
        val gestor = NotificationManagerCompat.from(context)
        if (!gestor.areNotificationsEnabled()) return false
        return listOf(Notificador.CANAL_AVISOS, Notificador.CANAL_ALARMA).all {
            gestor.getNotificationChannel(it)?.importance != NotificationManager.IMPORTANCE_NONE
        }
    }

    private companion object {
        const val OP_PANTALLA_COMPLETA = "android:use_full_screen_intent"
    }
}
