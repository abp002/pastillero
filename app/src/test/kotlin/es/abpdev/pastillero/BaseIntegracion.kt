package es.abpdev.pastillero

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import es.abpdev.pastillero.datos.BaseDeDatos
import es.abpdev.pastillero.dominio.ClaveToma
import es.abpdev.pastillero.dominio.Medicamento
import es.abpdev.pastillero.dominio.Pauta
import es.abpdev.pastillero.sistema.Notificador
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

val ZONA: ZoneId = ZoneId.of("Europe/Madrid")
val HOY: LocalDate = LocalDate.of(2026, 9, 28)

fun t(h: Int, m: Int = 0, dia: LocalDate = HOY): Instant = ZonedDateTime.of(dia, LocalTime.of(h, m), ZONA).toInstant()

class RelojDePrueba(var ahora: Instant) : Reloj {
    override fun ahora() = ahora
}

/**
 * La app de verdad (Room, notificaciones, AlarmManager, WorkManager, receptores) sobre
 * Robolectric, con la BD en memoria y el reloj en la mano.
 */
@RunWith(RobolectricTestRunner::class)
abstract class BaseIntegracion {
    protected val app: PastilleroApp = ApplicationProvider.getApplicationContext()
    protected val reloj = RelojDePrueba(t(8))
    protected lateinit var c: Contenedor
    private lateinit var bd: BaseDeDatos

    protected val notificaciones: NotificationManager get() = app.getSystemService(NotificationManager::class.java)
    protected val alarmas: AlarmManager get() = app.getSystemService(AlarmManager::class.java)

    @Before
    fun montar() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        bd = BaseDeDatos.enMemoria(app)
        c = Contenedor(app, bd, reloj, zona = { ZONA })
        app.contenedor = c
    }

    @After
    fun desmontar() = bd.close()

    protected fun metformina() = Medicamento(
        id = 0,
        nombre = "Metformina",
        indicacion = "1 pastilla con la comida",
        pauta = Pauta.HorasFijas(listOf(LocalTime.of(9, 0), LocalTime.of(21, 0))),
        margen = Duration.ofMinutes(30),
        desde = t(8),
    )

    protected fun tension() = Medicamento(
        id = 0,
        nombre = "Tensión",
        indicacion = "1 pastilla",
        pauta = Pauta.HorasFijas(listOf(LocalTime.of(21, 0))),
        margen = Duration.ofMinutes(30),
        desde = t(8),
    )

    protected fun guardar(med: Medicamento): Long = runBlocking { c.repositorio.guardarMedicamento(med) }

    /** Lleva el reloj a [cuando] y revisa, como si hubiera saltado la alarma. */
    protected fun revisarA(cuando: Instant) = runBlocking {
        reloj.ahora = cuando
        c.revisor.revisar()
    }

    protected fun notificacion(clave: ClaveToma): Notification? =
        notificaciones.activeNotifications.find { it.tag == Notificador.etiqueta(clave) }?.notification

    /** La alarma que ha dejado programada (setAlarmClock), o null. */
    protected fun proximaAlarma(): Instant? = alarmas.nextAlarmClock?.let { Instant.ofEpochMilli(it.triggerTime) }

    /** Entrega un broadcast como lo haría Android y espera a que el receptor acabe. */
    protected fun entregar(intent: Intent) {
        app.sendBroadcast(intent)
        shadowOf(Looper.getMainLooper()).idle()
        runBlocking { c.esperarTrabajos() }
    }
}
