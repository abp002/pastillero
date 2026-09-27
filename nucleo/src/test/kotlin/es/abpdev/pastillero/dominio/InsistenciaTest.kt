package es.abpdev.pastillero.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Criterios 1 (insiste hasta Tomada o Silenciar) y 5 (posponer tiene límite). */
class InsistenciaTest {

    @Test
    fun `criterio 1 - suena a su hora y cada 5 min, a pantalla completa desde los 15`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 31))

        assertEquals(
            listOf("21:00 AVISO", "21:05 AVISO", "21:10 AVISO", "21:15 ALARMA", "21:20 ALARMA", "21:25 ALARMA", "21:30 ALARMA"),
            sim.sonidos(),
        )
    }

    @Test
    fun `criterio 1 - sigue sonando mientras no la marque y para al marcarla`() {
        val sim = Simulador(listOf(tension()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(23))
        assertEquals(25, sim.sonidos().size, "De 21:00 a 23:00, cada 5 min")

        sim.pulsarTomada(ClaveToma(2, t(21)), t(23, 2))
        sim.avanzarHasta(manana(20, 59))

        assertEquals(25, sim.sonidos().size)
        assertTrue(t(23, 2) to Accion.Retirar(ClaveToma(2, t(21))) in sim.registro)
    }

    @Test
    fun `criterio 1 - silenciar corta el sonido pero no cuenta como tomada`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 6))

        assertIs<Resultado.Hecho>(sim.pulsarSilenciar(ClaveToma(1, t(21)), t(21, 6)))
        sim.avanzarHasta(t(23))

        assertEquals(listOf("21:00 AVISO", "21:05 AVISO"), sim.sonidos())
        assertEquals(Estado.SILENCIADA, sim.toma(1, t(21)).estado)
        assertEquals(null, sim.toma(1, t(21)).tomadaEn)
        assertTrue(t(21, 6) to Accion.Retirar(ClaveToma(1, t(21))) in sim.registro)
    }

    @Test
    fun `criterio 1 - sin marcar, se cierra a mitad de camino hasta la siguiente`() {
        // 9:00 y 21:00: la de las 9:00 sin marcar suena hasta las 14:55 y a las 15:00 queda «no tomada».
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(8, 30))
        sim.avanzarHasta(t(20, 59))

        assertEquals("09:00 AVISO", sim.sonidos().first())
        assertEquals("14:55 ALARMA", sim.sonidos().last())
        assertEquals(Estado.NO_TOMADA, sim.toma(1, t(9)).estado)
        assertTrue(t(15) to Accion.Retirar(ClaveToma(1, t(9))) in sim.registro)

        sim.avanzarHasta(t(21, 1))
        assertEquals("21:00 AVISO", sim.sonidos().last())
    }

    @Test
    fun `criterio 1 - una silenciada tambien se cierra a mitad de camino`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(8, 30))
        sim.avanzarHasta(t(9, 1))
        sim.pulsarSilenciar(ClaveToma(1, t(9)), t(9, 1))
        sim.avanzarHasta(t(15, 1))

        assertFalse(sim.toma(1, t(9)).abierta)
        assertEquals(t(9, 1), sim.toma(1, t(9)).silenciadaEn)
    }

    @Test
    fun `no suenan las tomas de antes del alta`() {
        // Alta a las 15:00 con 9:00 y 21:00: la de las 9:00 de hoy no existe.
        val sim = Simulador(listOf(metformina(desde = t(15))))
        sim.revisar(t(15))
        sim.avanzarHasta(t(21, 1))

        assertEquals(listOf("21:00 AVISO"), sim.sonidos())
    }

    @Test
    fun `al desactivar un medicamento deja de sonar y se quita su aviso`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 6))

        sim.medicamentos = listOf(metformina().copy(activo = false))
        sim.revisar(t(21, 7))
        sim.avanzarHasta(manana(22))

        assertEquals(listOf("21:00 AVISO", "21:05 AVISO"), sim.sonidos())
        assertTrue(t(21, 7) to Accion.Retirar(ClaveToma(1, t(21))) in sim.registro)
    }

    @Test
    fun `criterio 5 - posponer dos veces y despues ya no se puede`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(20))
        val clave = ClaveToma(1, t(21))

        sim.avanzarHasta(t(21, 2))
        assertIs<Resultado.Hecho>(sim.pulsarPosponer(clave, t(21, 2)))
        sim.avanzarHasta(t(21, 12))
        assertIs<Resultado.Hecho>(sim.pulsarPosponer(clave, t(21, 12)))
        sim.avanzarHasta(t(21, 23))

        assertEquals(listOf("21:00 AVISO", "21:12 AVISO", "21:22 ALARMA"), sim.sonidos())
        val ultimo = sim.registro.last { it.second is Accion.Sonar }.second as Accion.Sonar
        assertFalse(ultimo.puedePosponer)
        assertIs<Resultado.NoPermitido>(sim.pulsarPosponer(clave, t(21, 23)))
    }

    @Test
    fun `criterio 5 - mientras quedan posposiciones, el aviso ofrece posponer`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 1))

        val primero = sim.registro.single { it.second is Accion.Sonar }.second as Accion.Sonar
        assertTrue(primero.puedePosponer)
    }

    @Test
    fun `criterio 4 - la misma alarma repetida no suena dos veces`() {
        val sim = Simulador(listOf(metformina()))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 5))
        val antes = sim.sonidos().size

        sim.revisar(t(21, 5, s = 1))
        sim.revisar(t(21, 5, s = 2))

        assertEquals(antes, sim.sonidos().size)
    }
}
