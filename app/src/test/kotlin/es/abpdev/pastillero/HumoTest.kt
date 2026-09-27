package es.abpdev.pastillero

import android.os.Looper
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import es.abpdev.pastillero.ui.AlarmaActivity
import es.abpdev.pastillero.ui.MainActivity
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * Humo: la app arranca y pinta sus dos pantallas con una toma sonando. Si el cableado
 * (contenedor, ViewModel, Compose) se rompe, esto falla antes que nada.
 */
class HumoTest : BaseIntegracion() {

    @Test
    fun `la app arranca y pinta la pantalla principal y la de alarma sin romperse`() {
        guardar(metformina())
        revisarA(t(21, 20))

        val principal = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        assertIs<ComposeView>(principal.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))

        val alarma = Robolectric.buildActivity(AlarmaActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        assertIs<ComposeView>(alarma.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
        assertFalse(principal.isFinishing)
    }
}
