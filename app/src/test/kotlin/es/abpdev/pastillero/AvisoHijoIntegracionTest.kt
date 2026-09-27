package es.abpdev.pastillero

import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import com.sun.net.httpserver.HttpServer
import es.abpdev.pastillero.dominio.ClaveToma
import es.abpdev.pastillero.dominio.MensajeHijo
import es.abpdev.pastillero.sistema.AvisoHijoWorker
import es.abpdev.pastillero.sistema.ColaAvisosHijo
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Criterio 6 con WorkManager y un ntfy de mentira en localhost. */
class AvisoHijoIntegracionTest : BaseIntegracion() {
    private var servidor: HttpServer? = null
    private val recibidos = mutableListOf<String>()

    @After
    fun pararServidor() {
        servidor?.stop(0)
    }

    private fun trabajos(clave: ClaveToma): List<WorkInfo> =
        WorkManager.getInstance(app).getWorkInfosForUniqueWork(ColaAvisosHijo.nombre(clave)).get()

    @Test
    fun `criterio 6 - a los 30 min encola un solo aviso aunque se revise de mas`() {
        val id = guardar(metformina())
        revisarA(t(21))

        revisarA(t(21, 30))
        revisarA(t(21, 30))
        revisarA(t(21, 31))

        assertEquals(1, trabajos(ClaveToma(id, t(21))).size)
    }

    @Test
    fun `criterio 6 - si la marca despues, el aviso de tomada va en la misma cadena, detras`() {
        val id = guardar(metformina())
        val clave = ClaveToma(id, t(21))
        revisarA(t(21))
        revisarA(t(21, 30))
        reloj.ahora = t(21, 50)

        runBlocking { c.revisor.marcarTomada(clave) }

        val cadena = trabajos(clave)
        assertEquals(2, cadena.size)
        // Sin red en el test: el primero espera red y el segundo espera al primero.
        assertTrue(cadena.all { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }, "$cadena")
    }

    @Test
    fun `criterio 6 - si la marca antes de los 30 min no se encola nada`() {
        val id = guardar(metformina())
        val clave = ClaveToma(id, t(21))
        revisarA(t(21))
        reloj.ahora = t(21, 20)
        runBlocking { c.revisor.marcarTomada(clave) }

        revisarA(t(21, 40))

        assertEquals(0, trabajos(clave).size)
    }

    @Test
    fun `el worker publica el mensaje en el tema configurado`() {
        val url = arrancarServidor(200)
        c.preferencias.cambiar { it.copy(ntfyServidor = url, ntfyTema = "pastillero-prueba") }

        val resultado = ejecutarWorker(MensajeHijo("Papá: Metformina sin confirmar", "Tocaba a las 21:00.", 4, listOf("pill")))

        assertEquals(ListenableWorker.Result.success(), resultado)
        assertTrue("\"topic\":\"pastillero-prueba\"" in recibidos.single())
        assertTrue("Papá: Metformina sin confirmar" in recibidos.single())
    }

    @Test
    fun `el worker pide reintentar si ntfy no contesta`() {
        c.preferencias.cambiar { it.copy(ntfyServidor = "http://127.0.0.1:9") } // puerto cerrado

        val resultado = ejecutarWorker(MensajeHijo("t", "c", 3, emptyList()))

        assertEquals(ListenableWorker.Result.retry(), resultado)
    }

    @Test
    fun `el worker no manda nada si apagaste el aviso mientras esperaba`() {
        val url = arrancarServidor(200)
        c.preferencias.cambiar { it.copy(ntfyServidor = url, avisos = it.avisos.copy(avisoHijoActivo = false)) }

        val resultado = ejecutarWorker(MensajeHijo("t", "c", 3, emptyList()))

        assertEquals(ListenableWorker.Result.success(), resultado)
        assertTrue(recibidos.isEmpty())
    }

    private fun ejecutarWorker(mensaje: MensajeHijo): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<AvisoHijoWorker>(app).setInputData(AvisoHijoWorker.datos(mensaje)).build().doWork()
    }

    private fun arrancarServidor(codigo: Int): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { intercambio ->
            recibidos += intercambio.requestBody.readBytes().toString(Charsets.UTF_8)
            intercambio.sendResponseHeaders(codigo, -1)
            intercambio.close()
        }
        s.start()
        servidor = s
        return "http://127.0.0.1:${s.address.port}"
    }
}
