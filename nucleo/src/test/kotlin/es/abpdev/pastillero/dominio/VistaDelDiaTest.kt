package es.abpdev.pastillero.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VistaDelDiaTest {

    private fun botones(sim: Simulador, ahora: java.time.Instant) =
        VistaDelDia.filas(ahora, sim.medicamentos, sim.tomas.values.toList(), ZONA)
            .map { "${it.medicamento.nombre} ${hhmm(it.programada)} ${it.boton}" }

    @Test
    fun `sonando, el boton marca la actual y la siguiente no tiene boton`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 3))

        assertEquals(
            listOf("Metformina 09:00 NINGUNO", "Metformina 21:00 TOMADA", "Metformina 09:00 NINGUNO"),
            botones(sim, t(21, 3)),
        )
    }

    @Test
    fun `sin ninguna abierta, el boton adelanta la siguiente`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(8, 30))
        sim.avanzarHasta(t(9, 2))
        sim.pulsarTomada(ClaveToma(1, t(9)), t(9, 2))

        assertEquals(
            listOf("Metformina 09:00 NINGUNO", "Metformina 21:00 ADELANTAR"),
            botones(sim, t(12)),
        )
    }

    @Test
    fun `adelantada, ya no hay boton hasta que pase su hora`() {
        val sim = Simulador(listOf(tension()))
        sim.revisar(t(19))
        sim.pulsarAdelantar(2, t(19, 30))

        assertEquals(listOf("Tensión 21:00 NINGUNO"), botones(sim, t(20)))
        assertEquals(listOf("Tensión 21:00 NINGUNO", "Tensión 21:00 ADELANTAR"), botones(sim, manana(10)))
    }

    @Test
    fun `cada medicamento lleva su boton`() {
        val sim = Simulador(listOf(metformina(), tension()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 1))
        sim.pulsarTomada(ClaveToma(2, t(21)), t(21, 1))

        assertEquals(
            listOf(
                "Metformina 09:00 NINGUNO",
                "Metformina 21:00 TOMADA",
                "Tensión 21:00 NINGUNO",
                "Metformina 09:00 NINGUNO",
                "Tensión 21:00 NINGUNO",
            ),
            botones(sim, t(21, 2)),
        )
    }

    @Test
    fun `ALE-244 - recien dada de alta no se ofrece marcar la de manana`() {
        // Alta a las 18:30 de una pastilla de las 9:00: la primera es mañana y hoy no hay nada que marcar.
        val sim = Simulador(listOf(metformina("09:00", desde = t(18, 30))))
        sim.revisar(t(18, 30))

        assertEquals(listOf("Metformina 09:00 NINGUNO"), botones(sim, t(18, 30)))
        assertIs<Resultado.NoPermitido>(Operaciones.adelantar(sim.medicamentos.single(), null, t(18, 30), ZONA))
    }

    @Test
    fun `ALE-244 - recien dada de alta si se ofrece la de hoy`() {
        val sim = Simulador(listOf(tension(desde = t(12))))
        sim.revisar(t(12))

        assertEquals(listOf("Tensión 21:00 ADELANTAR"), botones(sim, t(12)))
    }

    private fun sonando(sim: Simulador, ahora: java.time.Instant) =
        VistaDelDia.filas(ahora, sim.medicamentos, sim.tomas.values.toList(), ZONA)
            .filter { it.sonando }.map { "${it.medicamento.nombre} ${hhmm(it.programada)}" }

    @Test
    fun `sonando es la que toca ahora, no la pospuesta ni la silenciada ni la siguiente`() {
        val sim = Simulador(listOf(metformina(), tension()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 1))

        assertEquals(listOf("Metformina 21:00", "Tensión 21:00"), sonando(sim, t(21, 1)))

        sim.pulsarPosponer(ClaveToma(1, t(21)), t(21, 2))
        sim.pulsarSilenciar(ClaveToma(2, t(21)), t(21, 2))
        assertEquals(emptyList(), sonando(sim, t(21, 3)), "Pospuesta y silenciada no suenan")

        sim.avanzarHasta(t(21, 12))
        assertEquals(listOf("Metformina 21:00"), sonando(sim, t(21, 12)), "Acabada la posposición, vuelve a sonar")
    }

    @Test
    fun `una silenciada de anoche sigue con boton hasta su cierre`() {
        val sim = Simulador(listOf(tension(desde = ayer(8))))
        sim.revisar(ayer(20))
        sim.avanzarHasta(ayer(21, 1))
        sim.pulsarSilenciar(ClaveToma(2, ayer(21)), ayer(21, 1))

        assertEquals(listOf("Tensión 21:00 TOMADA", "Tensión 21:00 NINGUNO"), botones(sim, t(7)))
    }
}
