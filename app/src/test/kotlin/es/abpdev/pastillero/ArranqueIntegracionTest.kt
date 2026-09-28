package es.abpdev.pastillero

import android.content.Intent
import es.abpdev.pastillero.dominio.ClaveToma
import es.abpdev.pastillero.sistema.ArranqueReceiver
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Criterio 7: tras reiniciar o cambiar la hora, la alarma vuelve a quedar programada. */
class ArranqueIntegracionTest : BaseIntegracion() {

    @Test
    fun `criterio 7 - al arrancar el movil a las 20h queda programada la alarma de las 21h`() {
        guardar(metformina())
        reloj.ahora = t(20)

        entregar(Intent(Intent.ACTION_BOOT_COMPLETED).setClass(app, ArranqueReceiver::class.java))

        assertEquals(t(21), proximaAlarma())
    }

    @Test
    fun `criterio 7 - un aviso que se pierde con el reinicio vuelve a sonar al arrancar`() {
        val id = guardar(metformina())
        revisarA(t(21))
        notificaciones.cancelAll() // el reinicio se lleva las notificaciones
        reloj.ahora = t(21, 7)

        entregar(Intent(Intent.ACTION_BOOT_COMPLETED).setClass(app, ArranqueReceiver::class.java))

        assertNotNull(notificacion(ClaveToma(id, t(21))))
        assertEquals(t(21, 10), proximaAlarma())
    }

    @Test
    fun `ALE-243 criterio 5 - tras actualizarse la app queda programada la alarma`() {
        guardar(metformina())
        reloj.ahora = t(20)

        entregar(Intent(Intent.ACTION_MY_PACKAGE_REPLACED).setClass(app, ArranqueReceiver::class.java))

        assertEquals(t(21), proximaAlarma())
    }

    @Test
    fun `criterio 7 - al cambiar la hora del movil se reprograma`() {
        guardar(metformina().copy(desde = t(12))) // alta a mediodía: la de las 9:00 no existe
        reloj.ahora = t(12)

        entregar(Intent(Intent.ACTION_TIME_CHANGED).setClass(app, ArranqueReceiver::class.java))

        assertEquals(t(21), proximaAlarma())
    }

    @Test
    fun `un broadcast que no es de arranque ni de hora no hace nada`() {
        guardar(metformina())
        reloj.ahora = t(12)

        entregar(Intent("es.abpdev.otra.cosa").setClass(app, ArranqueReceiver::class.java))

        assertNull(proximaAlarma())
    }
}
