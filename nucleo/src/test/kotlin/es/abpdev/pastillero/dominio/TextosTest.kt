package es.abpdev.pastillero.dominio

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class TextosTest {

    @Test
    fun `duraciones como las diria una persona`() {
        assertEquals("40 min", Textos.duracion(Duration.ofMinutes(40)))
        assertEquals("1 h 30 min", Textos.duracion(Duration.ofMinutes(90)))
        assertEquals("1 h 5 min", Textos.duracion(Duration.ofMinutes(65)))
        assertEquals("2 h", Textos.duracion(Duration.ofHours(2)))
        assertEquals("40 min", Textos.duracion(Duration.ofSeconds(40 * 60 + 59)), "Los segundos no cuentan")
    }

    @Test
    fun `el margen incluye el borde`() {
        val prog = t(21)
        assertEquals(Puntualidad.A_TIEMPO, Textos.clasificar(prog, t(21, 30), MEDIA_HORA).puntualidad)
        assertEquals(Puntualidad.TARDE, Textos.clasificar(prog, t(21, 31), MEDIA_HORA).puntualidad)
        assertEquals(Puntualidad.A_TIEMPO, Textos.clasificar(prog, t(20, 30), MEDIA_HORA).puntualidad)
        assertEquals(Puntualidad.ADELANTADA, Textos.clasificar(prog, t(20, 29), MEDIA_HORA).puntualidad)
        assertEquals(Duration.ofMinutes(31), Textos.clasificar(prog, t(20, 29), MEDIA_HORA).diferencia)
    }

    @Test
    fun `estado de cada toma`() {
        val base = Toma(1, t(21))
        assertEquals("sin marcar", Textos.estado(base, MEDIA_HORA, ZONA))
        assertEquals(
            "silenciada a las 21:05, sin marcar",
            Textos.estado(base.copy(estado = Estado.SILENCIADA, silenciadaEn = t(21, 5)), MEDIA_HORA, ZONA),
        )
        assertEquals("no tomada", Textos.estado(base.copy(estado = Estado.NO_TOMADA), MEDIA_HORA, ZONA))
        assertEquals(
            "silenciada a las 21:05, no tomada",
            Textos.estado(base.copy(estado = Estado.NO_TOMADA, silenciadaEn = t(21, 5)), MEDIA_HORA, ZONA),
        )
        assertEquals(
            "22:05 · tarde, 1 h 5 min",
            Textos.estado(base.copy(estado = Estado.TOMADA, tomadaEn = t(22, 5)), MEDIA_HORA, ZONA),
        )
    }
}
