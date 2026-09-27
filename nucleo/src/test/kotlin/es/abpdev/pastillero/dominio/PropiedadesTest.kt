package es.abpdev.pastillero.dominio

import java.time.Duration
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tres días con pulsaciones y despertares al azar, 300 veces. En vez de casos de ejemplo,
 * comprueba lo que tiene que cumplirse siempre, pulse lo que pulse y se despierte cuando se despierte.
 */
class PropiedadesTest {

    @Test
    fun `invariantes con pulsaciones y despertares al azar`() {
        repeat(300) { semilla ->
            try {
                simular(semilla)
            } catch (e: AssertionError) {
                fail("Semilla $semilla: ${e.message}", e)
            }
        }
    }

    private fun simular(semilla: Int) {
        val r = Random(semilla)
        val meds = listOf(metformina(), tension(), cada8h())
        val sim = Simulador(meds, Ajustes(avisoHijoActivo = r.nextBoolean()))
        var ahora = t(8, 30)
        sim.revisar(ahora)
        val fin = ahora.plus(Duration.ofDays(3))
        while (ahora < fin) {
            ahora = ahora.plusSeconds(r.nextLong(1, 3 * 3600))
            sim.avanzarHasta(ahora)
            when (r.nextInt(8)) {
                0 -> sim.abiertas().randomOrNull(r)?.let { sim.pulsarTomada(it.clave, ahora) }
                1 -> sim.abiertas().randomOrNull(r)?.let { sim.pulsarSilenciar(it.clave, ahora) }
                2 -> sim.abiertas().randomOrNull(r)?.let { sim.pulsarPosponer(it.clave, ahora) }
                3 -> sim.pulsarAdelantar(meds.random(r).id, ahora)
                4 -> sim.revisar(ahora)
                else -> {}
            }
            comprobar(sim)
        }
    }

    private fun comprobar(sim: Simulador) {
        val porMed = sim.tomas.values.groupBy { it.medicamentoId }
        for ((id, tomas) in porMed) {
            assertTrue(tomas.count { it.abierta } <= 1, "Medicamento $id con más de una toma abierta")
            val med = sim.medicamentos.single { it.id == id }
            assertTrue(tomas.all { it.programada >= med.desde }, "Toma anterior al alta")
            tomas.filter { it.abierta }.forEach {
                assertTrue(sim.ahora < Calendario.cierre(med, it, sim.zona), "Toma abierta pasado su cierre: $it")
            }
        }

        val sonidos = sim.registro.filter { it.second is Accion.Sonar }.groupBy({ it.second.toma }, { it.first })
        for ((clave, horas) in sonidos) {
            val toma = sim.tomas.getValue(clave)
            horas.zipWithNext().forEach { (a, b) ->
                assertTrue(Duration.between(a, b) >= sim.ajustes.intervaloAvisos, "Dos avisos seguidos de $clave: $a y $b")
            }
            val resuelta = toma.tomadaEn ?: toma.silenciadaEn
            if (resuelta != null && toma.programada <= resuelta) {
                assertTrue(horas.all { it <= resuelta }, "Sonó después de resolverse: $toma")
            }
            if (toma.estado == Estado.TOMADA && toma.tomadaEn!! < toma.programada) {
                assertTrue(horas.isEmpty(), "Sonó una adelantada: $toma")
            }
        }

        val hijo = sim.registro.filter { it.second is Accion.AvisarHijo }.map { it.second as Accion.AvisarHijo }
        if (!sim.ajustes.avisoHijoActivo) assertTrue(hijo.isEmpty(), "Aviso al hijo con el interruptor apagado")
        for ((clave, avisos) in hijo.groupBy { it.toma }) {
            val tipos = avisos.map { it.tipo }
            assertTrue(
                tipos == listOf(AvisoHijo.SIN_CONFIRMAR) || tipos == listOf(AvisoHijo.SIN_CONFIRMAR, AvisoHijo.TOMADA),
                "Avisos al hijo de $clave: $tipos",
            )
            if (AvisoHijo.TOMADA in tipos) {
                assertTrue(sim.tomas.getValue(clave).estado == Estado.TOMADA, "Aviso de tomada sin estar tomada")
            }
        }
    }
}
