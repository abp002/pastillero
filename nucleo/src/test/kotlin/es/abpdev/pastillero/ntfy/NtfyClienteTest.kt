package es.abpdev.pastillero.ntfy

import com.sun.net.httpserver.HttpServer
import es.abpdev.pastillero.dominio.MensajeHijo
import java.io.IOException
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Contra un servidor HTTP de verdad en localhost: método, ruta, cabeceras y cuerpo tal cual llegan. */
class NtfyClienteTest {
    private data class Peticion(val metodo: String, val ruta: String, val tipo: String?, val cuerpo: String)

    private lateinit var servidor: HttpServer
    private val recibidas = mutableListOf<Peticion>()
    private var codigo = 200

    private val url get() = "http://127.0.0.1:${servidor.address.port}"

    private val mensaje = MensajeHijo("Papá: Metformina sin confirmar", "Tocaba a las 21:00.\n\"Ojo\"", 4, listOf("pill"))

    @BeforeTest
    fun arrancar() {
        servidor = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        servidor.createContext("/") { intercambio ->
            recibidas += Peticion(
                intercambio.requestMethod,
                intercambio.requestURI.path,
                intercambio.requestHeaders.getFirst("Content-Type"),
                intercambio.requestBody.readBytes().toString(Charsets.UTF_8),
            )
            intercambio.sendResponseHeaders(codigo, -1)
            intercambio.close()
        }
        servidor.start()
    }

    @AfterTest
    fun parar() = servidor.stop(0)

    @Test
    fun `publica un JSON en la raiz con el tema, las tildes y la prioridad`() {
        NtfyCliente(url).publicar("pastillero-abc", mensaje)

        val peticion = recibidas.single()
        assertEquals("POST", peticion.metodo)
        assertEquals("/", peticion.ruta)
        assertEquals("application/json; charset=utf-8", peticion.tipo)
        assertEquals(
            """{"topic":"pastillero-abc","title":"Papá: Metformina sin confirmar","message":"Tocaba a las 21:00.\n\"Ojo\"","priority":4,"tags":["pill"]}""",
            peticion.cuerpo,
        )
    }

    @Test
    fun `con barra final en el servidor no duplica la barra`() {
        NtfyCliente("$url/").publicar("t", mensaje)

        assertEquals("/", recibidas.single().ruta)
    }

    @Test
    fun `si el servidor responde con error lanza IOException, para reintentar`() {
        codigo = 500

        assertFailsWith<IOException> { NtfyCliente(url).publicar("t", mensaje) }
    }

    @Test
    fun `si no hay servidor lanza IOException`() {
        val caido = url
        servidor.stop(0)

        assertFailsWith<IOException> { NtfyCliente(caido, timeoutMs = 2_000).publicar("t", mensaje) }
        assertTrue(recibidas.isEmpty())
    }
}
