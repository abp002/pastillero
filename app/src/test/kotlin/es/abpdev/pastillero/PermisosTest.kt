package es.abpdev.pastillero

import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.PowerManager
import android.provider.Settings
import es.abpdev.pastillero.sistema.Notificador
import es.abpdev.pastillero.sistema.Permiso
import es.abpdev.pastillero.sistema.Permisos
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowSettings
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Criterio 8: la pantalla principal sabe exactamente qué falta y adónde llevar para arreglarlo. */
class PermisosTest : BaseIntegracion() {
    private var pantallaCompleta = true
    private val permisos by lazy { Permisos(app) { pantallaCompleta } }

    @Before
    fun todoConcedido() {
        shadowOf(app.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(app.packageName, true)
        modoPantallaCompleta(AppOpsManager.MODE_ALLOWED)
        shadowOf(app.packageManager).setCanRequestPackageInstalls(true)
        ShadowSettings.setCanDrawOverlays(true)
    }

    private fun modoPantallaCompleta(modo: Int) = shadowOf(app.getSystemService(AppOpsManager::class.java))
        .setMode("android:use_full_screen_intent", app.applicationInfo.uid, app.packageName, modo)

    @Test
    fun `criterio 8 - con todo concedido no falta nada`() {
        assertEquals(emptyList(), permisos.faltan())
    }

    @Test
    fun `criterio 8 - sin notificaciones lo dice`() {
        shadowOf(notificaciones).setNotificationsEnabled(false)

        assertEquals(listOf(Permiso.NOTIFICACIONES), permisos.faltan())
    }

    @Test
    fun `criterio 8 - un canal apagado a mano cuenta como sin notificaciones`() {
        notificaciones.deleteNotificationChannel(Notificador.CANAL_ALARMA)
        notificaciones.createNotificationChannel(NotificationChannel(Notificador.CANAL_ALARMA, "Alarma", NotificationManager.IMPORTANCE_NONE))

        assertEquals(listOf(Permiso.NOTIFICACIONES), permisos.faltan())
    }

    @Test
    fun `criterio 8 - sin alarmas exactas lo dice`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        assertEquals(listOf(Permiso.ALARMAS_EXACTAS), permisos.faltan())
    }

    @Test
    fun `criterio 8 - sin pantalla completa lo dice`() {
        pantallaCompleta = false

        assertEquals(listOf(Permiso.PANTALLA_COMPLETA), permisos.faltan())
    }

    @Test
    fun `ALE-239 - con el permiso en modo por defecto pide la pantalla completa aunque el sistema diga que si`() {
        // Android 16: canUseFullScreenIntent() dice que sí y el interruptor de Ajustes sale activado,
        // pero al entregar la alarma el sistema rechaza la pantalla completa.
        modoPantallaCompleta(AppOpsManager.MODE_DEFAULT)

        assertEquals(listOf(Permiso.PANTALLA_COMPLETA), permisos.faltan())
    }

    @Test
    fun `ALE-239 - el aviso explica que si ya sale activado hay que apagarlo y encenderlo`() {
        assertTrue("apágalo y vuelve a encenderlo" in Permiso.PANTALLA_COMPLETA.porQue)
    }

    @Test
    fun `criterio 8 - con ahorro de bateria lo dice`() {
        shadowOf(app.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(app.packageName, false)

        assertEquals(listOf(Permiso.BATERIA), permisos.faltan())
    }

    @Test
    fun `ALE-243 criterio 6 - sin permiso para instalar actualizaciones lo dice`() {
        shadowOf(app.packageManager).setCanRequestPackageInstalls(false)

        assertEquals(listOf(Permiso.ACTUALIZAR), permisos.faltan())
    }

    @Test
    fun `sin permiso para superponerse lo dice`() {
        ShadowSettings.setCanDrawOverlays(false)

        assertEquals(listOf(Permiso.SUPERPONER), permisos.faltan())
    }

    @Test
    fun `criterio 8 - cada permiso lleva a su ajuste`() {
        assertEquals(
            listOf(
                Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            ),
            Permiso.entries.map { permisos.ajuste(it).action },
        )
        assertEquals("package:${app.packageName}", permisos.ajuste(Permiso.BATERIA).dataString)
    }

    @Test
    fun `sin alarmas exactas programa igualmente una aproximada`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        guardar(metformina().copy(desde = t(12))) // alta a mediodía: la de las 9:00 no existe

        revisarA(t(12))

        assertEquals(1, shadowOf(alarmas).scheduledAlarms.size)
        assertEquals(t(21).toEpochMilli(), shadowOf(alarmas).peekNextScheduledAlarm()?.triggerAtMs)
    }
}
