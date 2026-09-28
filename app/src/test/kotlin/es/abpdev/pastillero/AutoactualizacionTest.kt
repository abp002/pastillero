package es.abpdev.pastillero

import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.sun.net.httpserver.HttpServer
import es.abpdev.pastillero.actualizacion.ClienteGithub
import es.abpdev.pastillero.actualizacion.Version
import es.abpdev.pastillero.datos.BaseDeDatos
import es.abpdev.pastillero.sistema.ActualizacionWorker
import es.abpdev.pastillero.sistema.InstalacionReceiver
import es.abpdev.pastillero.sistema.Notificador
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** ALE-243: la app se actualiza sola desde las releases, contra un GitHub de mentira en localhost. */
class AutoactualizacionTest : BaseIntegracion() {
    private lateinit var servidor: HttpServer
    private var etiqueta = ""
    private var descargas = 0
    private val apk = ByteArray(50_000) { (it % 7).toByte() }
    private val sesiones = mutableListOf<String>()

    private val instalada: Version
        get() = Version.leer(app.packageManager.getPackageInfo(app.packageName, 0).versionName!!)!!

    @Before
    fun montarGithub() {
        servidor = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val base = "http://127.0.0.1:${servidor.address.port}"
        servidor.createContext("/repos/abp002/pastillero/releases/latest") { intercambio ->
            val json = """{"tag_name":"$etiqueta","assets":[{"name":"p.apk","browser_download_url":"$base/descargas/p.apk"}]}"""
            intercambio.sendResponseHeaders(200, json.toByteArray().size.toLong())
            intercambio.responseBody.use { it.write(json.toByteArray()) }
        }
        servidor.createContext("/descargas/") { intercambio ->
            descargas++
            intercambio.sendResponseHeaders(200, apk.size.toLong())
            intercambio.responseBody.use { it.write(apk) }
        }
        servidor.start()
        // Mismo contenedor que la base, pero con GitHub apuntando al servidor local.
        c = Contenedor(app, BaseDeDatos.enMemoria(app), reloj, zona = { ZONA }, github = ClienteGithub("abp002/pastillero", api = base))
        app.contenedor = c
        shadowOf(app.packageManager).setCanRequestPackageInstalls(true)
        app.packageManager.packageInstaller.registerSessionCallback(object : PackageInstaller.SessionCallback() {
            override fun onCreated(id: Int) { sesiones += "creada" }
            override fun onBadgingChanged(id: Int) = Unit
            override fun onActiveChanged(id: Int, active: Boolean) = Unit
            override fun onProgressChanged(id: Int, progress: Float) = Unit
            override fun onFinished(id: Int, success: Boolean) { sesiones += if (success) "instalada" else "fallida" }
        })
    }

    @After
    fun pararGithub() = servidor.stop(0)

    private fun trabajar(): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<ActualizacionWorker>(ApplicationProvider.getApplicationContext()).build().doWork()
    }.also { shadowOf(Looper.getMainLooper()).idle() }

    private fun siguiente() = instalada.let { "v${it.mayor}.${it.menor}.${it.parche + 1}" }

    @Test
    fun `criterio 1 - con una version mayor publicada la descarga y la instala`() {
        etiqueta = siguiente()

        assertEquals(ListenableWorker.Result.success(), trabajar())

        assertEquals(1, descargas)
        assertEquals(listOf("creada", "instalada"), sesiones)
    }

    @Test
    fun `criterio 2 - con la misma version no descarga nada`() {
        etiqueta = "v$instalada"

        assertEquals(ListenableWorker.Result.success(), trabajar())

        assertEquals(0, descargas)
        assertTrue(sesiones.isEmpty())
    }

    @Test
    fun `criterio 3 - con una toma sonando espera y no descarga`() {
        etiqueta = siguiente()
        guardar(metformina())
        revisarA(t(21, 5))

        assertEquals(ListenableWorker.Result.retry(), trabajar())

        assertEquals(0, descargas)
    }

    @Test
    fun `criterio 4 - sin red lo reintenta mas tarde`() {
        etiqueta = siguiente()
        servidor.stop(0)

        assertEquals(ListenableWorker.Result.retry(), trabajar())
    }

    @Test
    fun `sin permiso para instalar no descarga en balde`() {
        etiqueta = siguiente()
        shadowOf(app.packageManager).setCanRequestPackageInstalls(false)

        assertEquals(ListenableWorker.Result.success(), trabajar())

        assertEquals(0, descargas)
    }

    @Test
    fun `criterio 1 - si Android pide confirmacion deja una notificacion que lleva a actualizar`() {
        val confirmar = Intent("android.content.pm.action.CONFIRM_INSTALL")

        entregar(
            Intent(app, InstalacionReceiver::class.java)
                .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION)
                .putExtra(Intent.EXTRA_INTENT, confirmar),
        )

        val aviso = assertNotNull(notificaciones.activeNotifications.find { it.tag == Notificador.ETIQUETA_ACTUALIZACION })
        assertEquals("android.content.pm.action.CONFIRM_INSTALL", shadowOf(aviso.notification.contentIntent).savedIntent.action)
    }
}
