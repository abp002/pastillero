package es.abpdev.pastillero.sistema

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import es.abpdev.pastillero.ui.MainActivity
import java.time.Instant

/**
 * La única alarma de la app: la próxima revisión. Programarla otra vez sustituye a la anterior,
 * así nunca queda una alarma vieja que suene tras marcar la toma.
 */
class Despertador(private val context: Context) {
    private val alarmas = context.getSystemService(AlarmManager::class.java)

    fun programar(cuando: Instant?) {
        val revisar = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, AlarmaReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (cuando == null) {
            alarmas.cancel(revisar)
            return
        }
        if (Build.VERSION.SDK_INT >= 31 && !alarmas.canScheduleExactAlarms()) {
            // Sin permiso de alarma exacta, al menos una aproximada; la pantalla principal lo avisa en rojo.
            alarmas.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando.toEpochMilli(), revisar)
            return
        }
        // Como un despertador: salta aunque el móvil esté en reposo profundo (Doze).
        val mostrar = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarmas.setAlarmClock(AlarmManager.AlarmClockInfo(cuando.toEpochMilli(), mostrar), revisar)
    }
}
