package es.abpdev.pastillero.actualizacion

import es.abpdev.pastillero.dominio.ClaveToma
import es.abpdev.pastillero.dominio.Simulador
import es.abpdev.pastillero.dominio.ZONA
import es.abpdev.pastillero.dominio.metformina
import es.abpdev.pastillero.dominio.t
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActualizadorTest {
    private val url = "https://github.com/abp002/pastillero/releases/download/v0.2.1/pastillero-0.2.1.apk"

    @Test
    fun `las versiones se leen con o sin v y se comparan por numeros`() {
        assertEquals(Version(0, 2, 1), Version.leer("v0.2.1"))
        assertEquals(Version(0, 2, 1), Version.leer("0.2.1"))
        assertTrue(Version.leer("v1.10.0")!! > Version.leer("v1.9.9")!!, "1.10.0 > 1.9.9, no por orden de texto")
    }

    @Test
    fun `lo que no es X Y Z no es una version`() {
        listOf("latest", "v1.2", "1.2.3-beta", "", "v1.2.3.4", "1.x.3").forEach {
            assertNull(Version.leer(it), it)
        }
    }

    @Test
    fun `criterio 1 - una version mayor se descarga`() {
        val publicada = Publicada(Version(0, 2, 1), url)

        assertEquals(publicada, Actualizador.decidir("0.2.0", publicada))
    }

    @Test
    fun `criterio 2 - igual, mas antigua, ninguna o una instalada rara no descargan nada`() {
        assertNull(Actualizador.decidir("0.2.1", Publicada(Version(0, 2, 1), url)))
        assertNull(Actualizador.decidir("0.3.0", Publicada(Version(0, 2, 1), url)))
        assertNull(Actualizador.decidir("0.2.0", null))
        assertNull(Actualizador.decidir("0.2.0-debug", Publicada(Version(0, 2, 1), url)))
    }

    @Test
    fun `criterio 3 - con una toma sonando no es buen momento`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 5))

        assertFalse(Actualizador.esBuenMomento(t(21, 5), sim.medicamentos, sim.tomas.values.toList(), ZONA))
    }

    @Test
    fun `criterio 3 - con una silenciada sin marcar tampoco`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 1))
        sim.pulsarSilenciar(ClaveToma(1, t(21)), t(21, 1))

        assertFalse(Actualizador.esBuenMomento(t(21, 30), sim.medicamentos, sim.tomas.values.toList(), ZONA))
    }

    @Test
    fun `criterio 3 - a menos de 15 min de la siguiente no, a mas si`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(8, 30))
        sim.avanzarHasta(t(9, 1))
        sim.pulsarTomada(ClaveToma(1, t(9)), t(9, 1))
        val tomas = sim.tomas.values.toList()

        assertTrue(Actualizador.esBuenMomento(t(12), sim.medicamentos, tomas, ZONA))
        assertTrue(Actualizador.esBuenMomento(t(20, 44), sim.medicamentos, tomas, ZONA))
        assertFalse(Actualizador.esBuenMomento(t(20, 46), sim.medicamentos, tomas, ZONA))
    }

    @Test
    fun `sin pastillas cualquier momento es bueno`() {
        assertTrue(Actualizador.esBuenMomento(t(12), emptyList(), emptyList(), ZONA))
    }
}
