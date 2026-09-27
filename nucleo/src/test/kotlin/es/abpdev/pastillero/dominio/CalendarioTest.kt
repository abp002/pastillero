package es.abpdev.pastillero.dominio

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Criterios 7 (reinicio y cambio de hora) y 9 (recalcular según la pauta). */
class CalendarioTest {

    @Test
    fun `criterio 7 - tras reiniciar a las 20h suena a las 21h`() {
        val antes = Simulador(listOf(metformina()))
        antes.revisar(t(8, 30))
        antes.avanzarHasta(t(9, 5))
        antes.pulsarTomada(ClaveToma(1, t(9)), t(9, 5))
        antes.avanzarHasta(t(19, 59))

        // Reinicio: la alarma programada se pierde; las tomas siguen en la BD.
        val despues = Simulador(antes.medicamentos, tomas = antes.tomas)
        val plan = despues.revisar(t(20))
        despues.avanzarHasta(t(21, 1))

        assertEquals(t(21), plan.proximaRevision)
        assertEquals(listOf("21:00 AVISO"), despues.sonidos())
    }

    @Test
    fun `criterio 7 - la manana despues de atrasar la hora suena a las 9 de la hora nueva`() {
        // En Madrid, la noche del 25 al 26 de octubre de 2025, a las 3:00 volvieron a ser las 2:00.
        val sabado = LocalDate.of(2025, 10, 25)
        val domingo = sabado.plusDays(1)
        assertNotEquals(
            ZONA.rules.getOffset(t(12, dia = sabado)),
            ZONA.rules.getOffset(t(12, dia = domingo)),
            "Esa noche tiene que haber cambio de hora",
        )
        val sim = Simulador(listOf(metformina(desde = t(8, dia = sabado))))
        sim.revisar(t(20, dia = sabado))
        sim.avanzarHasta(t(21, dia = sabado))
        sim.pulsarTomada(ClaveToma(1, t(21, dia = sabado)), t(21, 1, dia = sabado))

        sim.avanzarHasta(t(9, 1, dia = domingo))

        val (cuando, _) = sim.registro.last { it.second is Accion.Sonar }
        assertEquals(LocalTime.of(9, 0), cuando.atZone(ZONA).toLocalTime())
        assertEquals(Instant.parse("2025-10-26T08:00:00Z"), cuando)
    }

    @Test
    fun `criterio 7 - el dia que se adelanta la hora tambien suena a su hora`() {
        // 29 de marzo de 2026: a las 2:00 pasan a ser las 3:00.
        val sabado = LocalDate.of(2026, 3, 28)
        val domingo = sabado.plusDays(1)
        val sim = Simulador(listOf(metformina(desde = t(8, dia = sabado))))
        sim.revisar(t(20, dia = sabado))
        sim.avanzarHasta(t(21, dia = sabado))
        sim.pulsarTomada(ClaveToma(1, t(21, dia = sabado)), t(21, 1, dia = sabado))

        sim.avanzarHasta(t(9, 1, dia = domingo))

        val (cuando, _) = sim.registro.last { it.second is Accion.Sonar }
        assertEquals(LocalTime.of(9, 0), cuando.atZone(ZONA).toLocalTime())
    }

    @Test
    fun `criterio 9 - cada 8 h recalcula desde la toma real`() {
        val sim = Simulador(listOf(cada8h()))
        sim.revisar(t(8, 30))
        sim.avanzarHasta(t(11))
        sim.pulsarTomada(ClaveToma(3, t(9)), t(11))

        sim.avanzarHasta(t(19, 1))

        assertEquals("19:00 AVISO", sim.sonidos().last())
        assertTrue(sim.sonidos(desde = t(11, 1)).none { it.startsWith("17:") })
    }

    @Test
    fun `criterio 9 - cada 8 h sin marcar, la siguiente sigue desde la que tocaba`() {
        val sim = Simulador(listOf(cada8h()))
        sim.revisar(t(8, 30))

        sim.avanzarHasta(t(17, 1))

        assertEquals(Estado.NO_TOMADA, sim.toma(3, t(9)).estado, "Se cierra a las 13:00, a mitad de camino")
        assertEquals("12:55 ALARMA", sim.sonidos(desde = t(9)).last { it < "17:00" })
        assertEquals("17:00 AVISO", sim.sonidos().last())
    }

    @Test
    fun `criterio 9 - cada 8 h adelantada, la siguiente cuenta desde la hora real`() {
        val sim = Simulador(listOf(cada8h()))
        sim.revisar(t(8, 30))
        sim.avanzarHasta(t(9, 2))
        sim.pulsarTomada(ClaveToma(3, t(9)), t(9, 2))
        sim.avanzarHasta(t(16))

        sim.pulsarAdelantar(3, t(16))
        sim.avanzarHasta(manana(0, 1))

        assertEquals(listOf("00:00 AVISO"), sim.sonidos(desde = t(9, 3)))
    }

    @Test
    fun `criterio 9 - con horas fijas, tomarse la de las 9h a las 11h no mueve la de las 21h`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(8, 30))
        sim.avanzarHasta(t(11))
        sim.pulsarTomada(ClaveToma(1, t(9)), t(11))

        sim.avanzarHasta(t(21, 1))

        assertEquals(listOf("21:00 AVISO"), sim.sonidos(desde = t(11, 1)))
    }

    @Test
    fun `cada 8 h dada de alta despues de la primera toma empieza por la siguiente`() {
        // Alta a las 10:00 diciendo que la primera fue a las 9:00: no suena ya, suena a las 17:00.
        val sim = Simulador(listOf(cada8h(primera = t(9), desde = t(10))))
        sim.revisar(t(10))

        sim.avanzarHasta(t(17, 1))

        assertEquals(listOf("17:00 AVISO"), sim.sonidos())
    }

    @Test
    fun `el cierre es la mitad del camino hasta la siguiente`() {
        assertEquals(t(15), Calendario.cierre(metformina(), Toma(1, t(9)), ZONA))
        assertEquals(manana(9), Calendario.cierre(tension(), Toma(2, t(21)), ZONA))
        assertEquals(t(13), Calendario.cierre(cada8h(), Toma(3, t(9)), ZONA))
    }
}
