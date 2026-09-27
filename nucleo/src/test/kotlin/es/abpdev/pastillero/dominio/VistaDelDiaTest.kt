package es.abpdev.pastillero.dominio

import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `una silenciada de anoche sigue con boton hasta su cierre`() {
        val sim = Simulador(listOf(tension(desde = ayer(8))))
        sim.revisar(ayer(20))
        sim.avanzarHasta(ayer(21, 1))
        sim.pulsarSilenciar(ClaveToma(2, ayer(21)), ayer(21, 1))

        assertEquals(listOf("Tensión 21:00 TOMADA", "Tensión 21:00 NINGUNO"), botones(sim, t(7)))
    }
}
