package es.abpdev.pastillero

import es.abpdev.pastillero.dominio.ClaveToma
import es.abpdev.pastillero.dominio.Estado
import es.abpdev.pastillero.dominio.Resultado
import es.abpdev.pastillero.sistema.Notificador
import es.abpdev.pastillero.ui.AlarmaActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Criterios 1, 2 y 4 con notificaciones, alarmas y BD de verdad. */
class AvisosIntegracionTest : BaseIntegracion() {

    @Test
    fun `criterio 1 - a su hora publica el aviso con sus tres botones y deja la alarma a los 5 min`() {
        val id = guardar(metformina())

        revisarA(t(21))

        val aviso = assertNotNull(notificacion(ClaveToma(id, t(21))))
        assertEquals(Notificador.CANAL_AVISOS, aviso.channelId)
        assertEquals(listOf("Tomada", "Posponer 10 min", "Silenciar"), aviso.actions.map { it.title.toString() })
        assertNull(aviso.fullScreenIntent)
        assertEquals(t(21, 5), proximaAlarma())
        assertEquals(1, shadowOf(alarmas).scheduledAlarms.size, "Una sola alarma programada")
    }

    @Test
    fun `criterio 1 - desde los 15 min suena por el canal de alarma y a pantalla completa`() {
        val id = guardar(metformina())
        revisarA(t(21))

        revisarA(t(21, 15))

        val alarma = assertNotNull(notificacion(ClaveToma(id, t(21))))
        assertEquals(Notificador.CANAL_ALARMA, alarma.channelId)
        val pantalla = shadowOf(assertNotNull(alarma.fullScreenIntent)).savedIntent
        assertEquals(AlarmaActivity::class.java.name, pantalla.component?.className)
    }

    @Test
    fun `criterio 1 - el canal de alarma suena como un despertador`() {
        val canal = notificaciones.getNotificationChannel(Notificador.CANAL_ALARMA)

        assertEquals(android.media.AudioAttributes.USAGE_ALARM, canal.audioAttributes.usage)
        assertEquals("alarma", canal.sound?.lastPathSegment)
    }

    @Test
    fun `criterio 2 - al marcarla se quita el aviso y la siguiente alarma es la de manana`() {
        val id = guardar(metformina())
        revisarA(t(21))
        reloj.ahora = t(21, 12)

        val resultado = runBlocking { c.revisor.marcarTomada(ClaveToma(id, t(21))) }

        assertIs<Resultado.Hecho>(resultado)
        assertNull(notificacion(ClaveToma(id, t(21))))
        assertEquals(t(9, dia = HOY.plusDays(1)), proximaAlarma())
    }

    @Test
    fun `criterio 4 - la notificacion y la pantalla a la vez apuntan una sola toma`() {
        val id = guardar(metformina())
        revisarA(t(21))
        reloj.ahora = t(21, 12)
        val clave = ClaveToma(id, t(21))

        val resultados = runBlocking {
            List(2) { async(Dispatchers.Default) { c.revisor.marcarTomada(clave) } }.awaitAll()
        }

        assertEquals(1, resultados.count { it is Resultado.Hecho }, "$resultados")
        assertEquals(1, resultados.count { it is Resultado.YaTomada }, "$resultados")
        val toma = runBlocking { c.repositorio.toma(clave) }
        assertEquals(t(21, 12), toma?.tomadaEn)
    }

    @Test
    fun `criterio 4 - el boton Tomada de cada notificacion marca su toma y no otra`() {
        // Dos pastillas a la misma hora: si los PendingIntent se confundieran, marcar una marcaría la otra.
        val metforminaId = guardar(metformina())
        val tensionId = guardar(tension())
        revisarA(t(21))
        reloj.ahora = t(21, 3)

        val tomadaTension = assertNotNull(notificacion(ClaveToma(tensionId, t(21)))).actions.first { it.title == "Tomada" }
        entregar(shadowOf(tomadaTension.actionIntent).savedIntent)

        runBlocking {
            assertEquals(Estado.TOMADA, c.repositorio.toma(ClaveToma(tensionId, t(21)))?.estado)
            assertEquals(Estado.PENDIENTE, c.repositorio.toma(ClaveToma(metforminaId, t(21)))?.estado)
        }
        assertNull(notificacion(ClaveToma(tensionId, t(21))))
        assertNotNull(notificacion(ClaveToma(metforminaId, t(21))))
    }

    @Test
    fun `criterio 5 - el boton Posponer desaparece al agotar las posposiciones`() {
        val id = guardar(metformina())
        val clave = ClaveToma(id, t(21))
        revisarA(t(21))
        reloj.ahora = t(21, 1)
        runBlocking { c.revisor.posponer(clave) }
        revisarA(t(21, 11))
        reloj.ahora = t(21, 12)
        runBlocking { c.revisor.posponer(clave) }

        revisarA(t(21, 22))

        assertEquals(listOf("Tomada", "Silenciar"), assertNotNull(notificacion(clave)).actions.map { it.title.toString() })
    }
}
