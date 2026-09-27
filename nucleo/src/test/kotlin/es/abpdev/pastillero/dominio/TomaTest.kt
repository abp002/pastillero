package es.abpdev.pastillero.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Criterios 2 (hora real), 3 (adelantar sin doble dosis) y 4 (marcar dos veces cuenta una). */
class TomaTest {

    @Test
    fun `criterio 2 - marcada a las 21h12 queda a tiempo y deja de sonar`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 12))

        sim.pulsarTomada(ClaveToma(1, t(21)), t(21, 12))
        sim.avanzarHasta(manana(8, 59))

        assertEquals(listOf("21:00 AVISO", "21:05 AVISO", "21:10 AVISO"), sim.sonidos())
        assertEquals("21:12 · a tiempo", Textos.estado(sim.toma(1, t(21)), MEDIA_HORA, ZONA))
    }

    @Test
    fun `criterio 2 - marcada a las 21h40 queda tarde, 40 min`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 40))

        sim.pulsarTomada(ClaveToma(1, t(21)), t(21, 40))

        assertEquals("21:40 · tarde, 40 min", Textos.estado(sim.toma(1, t(21)), MEDIA_HORA, ZONA))
    }

    @Test
    fun `criterio 3 - adelantarla fuera del margen pide confirmacion`() {
        val med = tension()

        assertTrue(Operaciones.pideConfirmacion(med, t(21), t(19, 30)))
        assertFalse(Operaciones.pideConfirmacion(med, t(21), t(20, 30)), "Dentro del margen")
        assertFalse(Operaciones.pideConfirmacion(med, t(21), t(21, 10)), "Ya es su hora")
    }

    @Test
    fun `criterio 3 - adelantada, a su hora no suena y queda adelantada 1 h 30 min`() {
        val sim = Simulador(listOf(tension()))
        sim.revisar(t(19))

        assertIs<Resultado.Hecho>(sim.pulsarAdelantar(2, t(19, 30)))
        sim.avanzarHasta(manana(20, 59))

        assertEquals(emptyList(), sim.sonidos())
        assertEquals("19:30 · adelantada, 1 h 30 min", Textos.estado(sim.toma(2, t(21)), MEDIA_HORA, ZONA))
        sim.avanzarHasta(manana(21, 1))
        assertEquals(listOf("21:00 AVISO"), sim.sonidos(), "Al día siguiente vuelve a sonar")
    }

    @Test
    fun `criterio 3 - no se puede adelantar dos seguidas`() {
        val sim = Simulador(listOf(tension()))
        sim.revisar(t(19))

        sim.pulsarAdelantar(2, t(19, 30))

        assertEquals(Resultado.YaTomada(t(19, 30)), sim.pulsarAdelantar(2, t(19, 31)))
        assertEquals(1, sim.tomas.values.count { it.estado == Estado.TOMADA })
    }

    @Test
    fun `criterio 3 - no se adelanta la siguiente si la actual sigue abierta`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 3))

        assertIs<Resultado.NoPermitido>(sim.pulsarAdelantar(1, t(21, 3)))
    }

    @Test
    fun `criterio 3 - la de ayer silenciada no se confunde con la de hoy`() {
        // Regresión del «cierre a mitad de camino»: tensión a las 21:00. Ayer la silenció sin
        // tomarla; hoy a las 20:00 se la toma. Tiene que contar como la de hoy, y a las 21:00
        // no puede sonar (si sonara, se tomaría otra).
        val sim = Simulador(listOf(tension(desde = ayer(8))))
        sim.revisar(ayer(20))
        sim.avanzarHasta(ayer(21, 1))
        sim.pulsarSilenciar(ClaveToma(2, ayer(21)), ayer(21, 1))
        sim.avanzarHasta(t(20))

        val conBoton = VistaDelDia.filas(t(20), sim.medicamentos, sim.tomas.values.toList(), ZONA)
            .filter { it.boton != Boton.NINGUNO }
        assertEquals(listOf(t(21) to Boton.ADELANTAR), conBoton.map { it.programada to it.boton })

        sim.pulsarAdelantar(2, t(20))
        sim.avanzarHasta(t(21, 30))

        assertEquals(emptyList(), sim.sonidos(desde = t(20)))
        assertEquals(Estado.TOMADA, sim.toma(2, t(21)).estado)
        assertEquals(null, sim.toma(2, ayer(21)).tomadaEn)
    }

    @Test
    fun `criterio 4 - marcar dos veces cuenta una, con la hora de la primera`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 12))
        val clave = ClaveToma(1, t(21))

        assertIs<Resultado.Hecho>(sim.pulsarTomada(clave, t(21, 12)))
        assertEquals(Resultado.YaTomada(t(21, 12)), sim.pulsarTomada(clave, t(21, 12, s = 1)))

        assertEquals(t(21, 12), sim.toma(1, t(21)).tomadaEn)
    }

    @Test
    fun `criterio 4 - marcar despues de silenciar cuenta como tomada`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 6))
        val clave = ClaveToma(1, t(21))

        sim.pulsarSilenciar(clave, t(21, 6))
        assertIs<Resultado.Hecho>(sim.pulsarTomada(clave, t(21, 20)))

        assertEquals(Estado.TOMADA, sim.toma(1, t(21)).estado)
        assertEquals(t(21, 20), sim.toma(1, t(21)).tomadaEn)
    }

    @Test
    fun `una toma cerrada ya no se puede marcar`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(8, 30))
        sim.avanzarHasta(t(15, 1))

        assertIs<Resultado.NoPermitido>(sim.pulsarTomada(ClaveToma(1, t(9)), t(15, 1)))
    }

    @Test
    fun `posponer o silenciar una tomada no la cambia`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 1))
        val clave = ClaveToma(1, t(21))
        sim.pulsarTomada(clave, t(21, 1))

        assertEquals(Resultado.YaTomada(t(21, 1)), sim.pulsarSilenciar(clave, t(21, 2)))
        assertEquals(Resultado.YaTomada(t(21, 1)), sim.pulsarPosponer(clave, t(21, 2)))
        assertEquals(Estado.TOMADA, sim.toma(1, t(21)).estado)
    }
}
