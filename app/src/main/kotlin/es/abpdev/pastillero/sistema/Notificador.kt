package es.abpdev.pastillero.sistema

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import es.abpdev.pastillero.R
import es.abpdev.pastillero.dominio.ClaveToma
import es.abpdev.pastillero.dominio.Medicamento
import es.abpdev.pastillero.dominio.Nivel
import es.abpdev.pastillero.dominio.Textos
import es.abpdev.pastillero.ui.AlarmaActivity
import es.abpdev.pastillero.ui.MainActivity
import java.time.Duration
import java.time.ZoneId

/**
 * Una notificación por toma, identificada por su etiqueta. Volver a publicarla la hace sonar
 * otra vez; así «insiste» sin sonido en bucle: cada aviso suena una vez, cada X minutos.
 */
class Notificador(private val context: Context, private val zona: () -> ZoneId) {

    /** Los canales no se pueden cambiar una vez creados: para cambiar el sonido, canal nuevo. */
    fun crearCanales() {
        val avisos = NotificationChannel(CANAL_AVISOS, "Avisos de pastillas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Los primeros avisos de cada toma. Respetan el modo silencio."
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val alarma = NotificationChannel(CANAL_ALARMA, "Alarma de pastillas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Cuando lleva un rato sin marcarla. Suena como un despertador, aunque esté en silencio."
            setSound(
                sonidoAlarma(),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val actualizaciones = NotificationChannel(CANAL_ACTUALIZACIONES, "Actualizaciones", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Solo si el móvil pide confirmar una versión nueva de la app."
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(avisos, alarma, actualizaciones))
    }

    fun sonar(med: Medicamento, clave: ClaveToma, nivel: Nivel, puedePosponer: Boolean, posponer: Duration) {
        val hora = Textos.hora(clave.programada, zona())
        val indicacion = med.indicacion.ifBlank { "Te toca la pastilla" }
        val alarma = nivel == Nivel.ALARMA
        val constructor = NotificationCompat.Builder(context, if (alarma) CANAL_ALARMA else CANAL_AVISOS)
            .setSmallIcon(R.drawable.ic_pastilla)
            .setContentTitle("${med.nombre} · $hora")
            .setContentText(indicacion)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$indicacion\nCuando te la tomes, pulsa «Tomada»."))
            .setCategory(if (alarma) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setWhen(clave.programada.toEpochMilli())
            .setShowWhen(true)
            .setContentIntent(abrirApp())
            .addAction(0, "Tomada", accion(AccionReceiver.TOMADA, clave))
        if (puedePosponer) constructor.addAction(0, "Posponer ${posponer.toMinutes()} min", accion(AccionReceiver.POSPONER, clave))
        constructor.addAction(0, "Silenciar", accion(AccionReceiver.SILENCIAR, clave))
        if (alarma) constructor.setFullScreenIntent(pantallaAlarma(prueba = false), true)
        publicar(etiqueta(clave), constructor.build())
    }

    fun retirar(clave: ClaveToma) = NotificationManagerCompat.from(context).cancel(etiqueta(clave), ID)

    /** Para que el hijo compruebe, al instalarla, que suena con el móvil en silencio y bloqueado. */
    fun probar() {
        val notificacion = NotificationCompat.Builder(context, CANAL_ALARMA)
            .setSmallIcon(R.drawable.ic_pastilla)
            .setContentTitle("Prueba de alarma")
            .setContentText("Así sonará cuando lleve un rato sin marcar la pastilla.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pantallaAlarma(prueba = true))
            .setFullScreenIntent(pantallaAlarma(prueba = true), true)
            .build()
        publicar(ETIQUETA_PRUEBA, notificacion)
    }

    fun retirarPrueba() = NotificationManagerCompat.from(context).cancel(ETIQUETA_PRUEBA, ID)

    /** En Android anterior a 12 la actualización necesita un toque: se le deja aquí. */
    fun avisarActualizacion(confirmar: Intent) {
        val abrir = PendingIntent.getActivity(
            context,
            3,
            confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notificacion = NotificationCompat.Builder(context, CANAL_ACTUALIZACIONES)
            .setSmallIcon(R.drawable.ic_pastilla)
            .setContentTitle("Hay una versión nueva de Mis pastillas")
            .setContentText("Toca aquí y después «Actualizar».")
            .setAutoCancel(true)
            .setContentIntent(abrir)
            .build()
        publicar(ETIQUETA_ACTUALIZACION, notificacion)
    }

    private fun publicar(etiqueta: String, notificacion: Notification) {
        // Sin permiso no se puede; la pantalla principal lo enseña en rojo (criterio 8).
        val permitido = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (permitido) NotificationManagerCompat.from(context).notify(etiqueta, ID, notificacion)
    }

    /**
     * Cada toma lleva su propia URI: sin ella, los PendingIntent de dos tomas con la misma
     * acción serían «iguales» para Android y el segundo pisaría los extras del primero.
     */
    private fun accion(accion: String, clave: ClaveToma): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, AccionReceiver::class.java).setAction(accion).setData(AccionReceiver.uri(clave)),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun abrirApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun pantallaAlarma(prueba: Boolean): PendingIntent = PendingIntent.getActivity(
        context,
        if (prueba) 2 else 1,
        Intent(context, AlarmaActivity::class.java)
            .putExtra(AlarmaActivity.PRUEBA, prueba)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun sonidoAlarma(): Uri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.packageName)
        .appendPath(context.resources.getResourceTypeName(R.raw.alarma))
        .appendPath(context.resources.getResourceEntryName(R.raw.alarma))
        .build()

    companion object {
        const val CANAL_AVISOS = "avisos"
        const val CANAL_ALARMA = "alarma_v1"
        const val CANAL_ACTUALIZACIONES = "actualizaciones"
        const val ETIQUETA_ACTUALIZACION = "actualizacion"
        private const val ETIQUETA_PRUEBA = "prueba"
        private const val ID = 1

        fun etiqueta(clave: ClaveToma) = "toma-${clave.medicamentoId}-${clave.programada.toEpochMilli()}"
    }
}
