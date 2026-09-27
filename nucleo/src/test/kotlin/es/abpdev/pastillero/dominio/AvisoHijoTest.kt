package es.abpdev.pastillero.dominio

import kotlin.test.Test
import kotlin.test.assertEquals

/** Criterio 6: aviso al hijo, uno solo, y el cierre cuando por fin la toma. */
class AvisoHijoTest {

    @Test
    fun `criterio 6 - a los 30 min sin marcar, un solo aviso aunque se revise de mas`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 45))
        sim.revisar(t(21, 45))
        sim.revisar(t(21, 45, s = 1))

        assertEquals(listOf("21:30 SIN_CONFIRMAR"), sim.avisosHijo())
    }

    @Test
    fun `criterio 6 - cuando por fin la marca, le llega otro aviso, y solo uno`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 50))
        val clave = ClaveToma(1, t(21))

        sim.pulsarTomada(clave, t(21, 50))
        sim.pulsarTomada(clave, t(21, 51))

        assertEquals(listOf("21:30 SIN_CONFIRMAR", "21:50 TOMADA"), sim.avisosHijo())
    }

    @Test
    fun `criterio 6 - si la marca antes de los 30 min, no le llega nada`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 29))
        sim.pulsarTomada(ClaveToma(1, t(21)), t(21, 29))
        sim.avanzarHasta(t(23))

        assertEquals(emptyList(), sim.avisosHijo())
    }

    @Test
    fun `criterio 6 - si la silencia, le llega igual`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 5))
        sim.pulsarSilenciar(ClaveToma(1, t(21)), t(21, 5))
        sim.avanzarHasta(t(21, 31))

        assertEquals(listOf("21:30 SIN_CONFIRMAR"), sim.avisosHijo())
    }

    @Test
    fun `criterio 6 - con el interruptor apagado, nada`() {
        val sim = Simulador(listOf(metformina()), Ajustes(avisoHijoActivo = false))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 50))
        sim.pulsarTomada(ClaveToma(1, t(21)), t(21, 50))

        assertEquals(emptyList(), sim.avisosHijo())
    }

    @Test
    fun `criterio 6 - si lo apagas despues del primer aviso, no le llega el de tomada`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 31))
        sim.ajustes = sim.ajustes.copy(avisoHijoActivo = false)
        sim.pulsarTomada(ClaveToma(1, t(21)), t(21, 50))

        assertEquals(listOf("21:30 SIN_CONFIRMAR"), sim.avisosHijo())
    }
}
