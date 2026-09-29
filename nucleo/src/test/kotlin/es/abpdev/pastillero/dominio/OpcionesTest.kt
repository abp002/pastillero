package es.abpdev.pastillero.dominio

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Mientras una toma siga abierta, dentro de la app salen siempre sus tres opciones, suene o no.
 * Primera prueba real: silenciada, solo quedaba «Ya me la he tomado» y no se podía pedir que la recordase.
 */
class OpcionesTest {
    private val ajustes = Ajustes(posponer = Duration.ofMinutes(10))
    private val todas = setOf(Gesto.TOMADA, Gesto.POSPONER, Gesto.SILENCIAR)

    private fun sonando(): Pair<Simulador, ClaveToma> {
        val sim = Simulador(listOf(metformina()), ajustes)
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 1))
        return sim to ClaveToma(1, t(21))
    }

    @Test
    fun `sonando, las tres`() {
        val (sim, clave) = sonando()

        assertEquals(todas, Operaciones.opciones(sim.toma(1, clave.programada), t(21, 1), ajustes))
    }

    @Test
    fun `silenciada, siguen las tres`() {
        val (sim, clave) = sonando()
        sim.pulsarSilenciar(clave, t(21, 2))

        assertEquals(todas, Operaciones.opciones(sim.toma(1, clave.programada), t(21, 3), ajustes))
    }

    @Test
    fun `pospuesta y todavia sin volver a sonar, siguen las tres`() {
        val (sim, clave) = sonando()
        sim.pulsarPosponer(clave, t(21, 2))

        assertEquals(todas, Operaciones.opciones(sim.toma(1, clave.programada), t(21, 5), ajustes))
    }

    @Test
    fun `sin posposiciones que gastar, no se ofrece recordar`() {
        val (sim, clave) = sonando()
        sim.pulsarPosponer(clave, t(21, 2))
        sim.pulsarPosponer(clave, t(21, 12))

        assertEquals(
            setOf(Gesto.TOMADA, Gesto.SILENCIAR),
            Operaciones.opciones(sim.toma(1, clave.programada), t(21, 23), ajustes),
        )
    }

    @Test
    fun `tomada o cerrada, ninguna`() {
        val (sim, clave) = sonando()
        sim.pulsarTomada(clave, t(21, 2))

        assertEquals(emptySet(), Operaciones.opciones(sim.toma(1, clave.programada), t(21, 3), ajustes))
        assertEquals(emptySet(), Operaciones.opciones(Toma(1, t(9), Estado.NO_TOMADA), t(21, 3), ajustes))
    }

    @Test
    fun `recuerdamelo sobre una silenciada la vuelve a hacer sonar a los N min`() {
        val (sim, clave) = sonando()
        sim.pulsarSilenciar(clave, t(21, 2))

        assertIs<Resultado.Hecho>(sim.pulsarPosponer(clave, t(21, 3)))
        sim.avanzarHasta(t(21, 14))

        assertEquals("21:13", sim.sonidos(desde = t(21, 3)).firstOrNull()?.substringBefore(" "))
        assertEquals(1, sim.toma(1, clave.programada).posposiciones)
    }
}
