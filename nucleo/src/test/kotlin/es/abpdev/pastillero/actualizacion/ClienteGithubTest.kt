package es.abpdev.pastillero.actualizacion

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Contra un servidor HTTP de verdad que imita la API de GitHub y la descarga del APK. */
class ClienteGithubTest {
    private lateinit var servidor: HttpServer
    private var codigo = 200
    private var json = ""
    private val apk = ByteArray(300_000) { (it % 251).toByte() }
    private val cabeceras = mutableListOf<Map<String, String?>>()
    private lateinit var carpeta: File

    private val base get() = "http://127.0.0.1:${servidor.address.port}"

    private fun release(etiqueta: String, vararg ficheros: String) = """
        {"url":"x","tag_name":"$etiqueta","name":"$etiqueta","draft":false,"prerelease":false,
         "assets":[${ficheros.joinToString(",") { """{"name":"$it","size":9,"browser_download_url":"$base/descargas/$it"}""" }}]}
    """.trimIndent()

    @BeforeTest
    fun arrancar() {
        carpeta = Files.createTempDirectory("pastillero").toFile()
        servidor = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        servidor.createContext("/repos/abp002/pastillero/releases/latest") { intercambio ->
            cabeceras += mapOf(
                "User-Agent" to intercambio.requestHeaders.getFirst("User-Agent"),
                "Accept" to intercambio.requestHeaders.getFirst("Accept"),
            )
            val cuerpo = json.toByteArray()
            intercambio.sendResponseHeaders(codigo, if (codigo == 200) cuerpo.size.toLong() else -1)
            if (codigo == 200) intercambio.responseBody.use { it.write(cuerpo) }
            intercambio.close()
        }
        servidor.createContext("/descargas/") { intercambio ->
            if (codigo != 200) {
                intercambio.sendResponseHeaders(codigo, -1)
            } else {
                intercambio.sendResponseHeaders(200, apk.size.toLong())
                intercambio.responseBody.use { it.write(apk) }
            }
            intercambio.close()
        }
        servidor.start()
    }

    @AfterTest
    fun parar() {
        servidor.stop(0)
        carpeta.deleteRecursively()
    }

    private fun cliente() = ClienteGithub("abp002/pastillero", api = base, agente = "Pastillero/0.2.0")

    @Test
    fun `lee la ultima release con su APK y se identifica ante GitHub`() {
        json = release("v0.2.1", "pastillero-0.2.1.apk", "notas.txt")

        val publicada = cliente().ultima()

        assertEquals(Publicada(Version(0, 2, 1), "$base/descargas/pastillero-0.2.1.apk"), publicada)
        assertEquals("Pastillero/0.2.0", cabeceras.single()["User-Agent"])
        assertEquals("application/vnd.github+json", cabeceras.single()["Accept"])
    }

    @Test
    fun `criterio 2 - sin APK o con etiqueta rara no hay nada que instalar`() {
        json = release("v0.2.1", "notas.txt")
        assertNull(cliente().ultima())

        json = release("nightly", "pastillero.apk")
        assertNull(cliente().ultima())
    }

    @Test
    fun `sin ninguna release publicada no hay nada`() {
        codigo = 404

        assertNull(cliente().ultima())
    }

    @Test
    fun `criterio 4 - si GitHub falla o no hay red lanza IOException para reintentar`() {
        codigo = 500
        assertFailsWith<IOException> { cliente().ultima() }

        val caido = ClienteGithub("abp002/pastillero", api = "http://127.0.0.1:9", timeoutMs = 2_000)
        assertFailsWith<IOException> { caido.ultima() }
    }

    @Test
    fun `descarga el APK entero`() {
        val destino = File(carpeta, "actualizacion.apk")

        cliente().descargar("$base/descargas/pastillero-0.2.1.apk", destino)

        assertContentEquals(apk, destino.readBytes())
    }

    @Test
    fun `si la descarga falla no deja un fichero a medias`() {
        val destino = File(carpeta, "actualizacion.apk")
        codigo = 503

        assertFailsWith<IOException> { cliente().descargar("$base/descargas/pastillero-0.2.1.apk", destino) }

        assertFalse(destino.exists())
    }
}
