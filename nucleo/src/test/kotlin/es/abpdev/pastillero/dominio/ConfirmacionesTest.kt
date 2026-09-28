package es.abpdev.pastillero.dominio

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

/** Criterio 4 de la primera prueba real: tras pulsar, una confirmación que no deja dudas. */
class ConfirmacionesTest {
    private val clave = ClaveToma(1, t(21))

    private fun hecho(toma: Toma) = Resultado.Hecho(toma, emptyList())

    @Test
    fun `tomada - apuntada con su hora`() {
        val resultado = hecho(Toma(1, t(21), Estado.TOMADA, tomadaEn = t(21, 12)))

        assertEquals(
            Confirmacion(Confirmacion.Tipo.BIEN, "✓ Apuntada", "Metformina, a las 21:12"),
            Confirmaciones.de(Gesto.TOMADA, resultado, "Metformina", ZONA),
        )
    }

    @Test
    fun `recuerdamelo - dice a que hora vuelve a sonar`() {
        val resultado = hecho(Toma(1, t(21), pospuestaHasta = t(21, 22), posposiciones = 1))

        assertEquals(
            Confirmacion(Confirmacion.Tipo.LUEGO, "Te lo recuerdo a las 21:22", "Metformina"),
            Confirmaciones.de(Gesto.POSPONER, resultado, "Metformina", ZONA),
        )
    }

    @Test
    fun `dejar de sonar - recuerda que sigue sin marcar`() {
        val resultado = hecho(Toma(1, t(21), Estado.SILENCIADA, silenciadaEn = t(21, 5)))

        assertEquals(
            Confirmacion(
                Confirmacion.Tipo.APAGADA,
                "Alarma apagada",
                "Metformina sigue sin marcar. Cuando te la tomes, márcala aquí.",
            ),
            Confirmaciones.de(Gesto.SILENCIAR, resultado, "Metformina", ZONA),
        )
    }

    @Test
    fun `pulsar otra vez - ya estaba apuntada, con la hora de la primera`() {
        assertEquals(
            Confirmacion(Confirmacion.Tipo.BIEN, "✓ Ya estaba apuntada", "Metformina, a las 21:12"),
            Confirmaciones.de(Gesto.TOMADA, Resultado.YaTomada(t(21, 12)), "Metformina", ZONA),
        )
        assertEquals(
            Confirmacion(Confirmacion.Tipo.BIEN, "✓ Ya estaba apuntada", "Metformina, a las 21:12"),
            Confirmaciones.de(Gesto.POSPONER, Resultado.YaTomada(t(21, 12)), "Metformina", ZONA),
        )
    }

    @Test
    fun `lo que no se puede hacer se explica`() {
        assertEquals(
            Confirmacion(Confirmacion.Tipo.AVISO, "No se ha podido", "Ya no se puede posponer más"),
            Confirmaciones.de(Gesto.POSPONER, Resultado.NoPermitido("Ya no se puede posponer más"), "Metformina", ZONA),
        )
    }

    @Test
    fun `las confirmaciones salen del flujo real de pulsaciones`() {
        // El mismo texto que verá tras posponer de verdad en el simulador.
        val sim = Simulador(listOf(metformina()), Ajustes(posponer = Duration.ofMinutes(10)))
        sim.revisar(t(20))
        sim.avanzarHasta(t(21, 2))

        val resultado = sim.pulsarPosponer(clave, t(21, 2))

        assertEquals("Te lo recuerdo a las 21:12", Confirmaciones.de(Gesto.POSPONER, resultado, "Metformina", ZONA).titulo)
    }
}
