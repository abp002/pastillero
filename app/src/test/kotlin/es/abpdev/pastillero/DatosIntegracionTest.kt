package es.abpdev.pastillero

import es.abpdev.pastillero.datos.Preferencias
import es.abpdev.pastillero.dominio.AvisoHijo
import es.abpdev.pastillero.dominio.ClaveToma
import es.abpdev.pastillero.dominio.Estado
import es.abpdev.pastillero.dominio.Pauta
import es.abpdev.pastillero.dominio.Toma
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** La frontera con Room y SharedPreferences: lo que se guarda es lo que se lee. */
class DatosIntegracionTest : BaseIntegracion() {

    @Test
    fun `un medicamento a horas fijas y otro cada 8 h vuelven iguales de la BD`() = runBlocking {
        val fijo = metformina()
        val cada8 = tension().copy(nombre = "Antibiótico", pauta = Pauta.CadaIntervalo(Duration.ofHours(8), t(9)))

        val idFijo = c.repositorio.guardarMedicamento(fijo)
        val idCada8 = c.repositorio.guardarMedicamento(cada8)

        assertEquals(listOf(fijo.copy(id = idFijo), cada8.copy(id = idCada8)), c.repositorio.medicamentos())
    }

    @Test
    fun `guardar la misma toma dos veces la actualiza, no la duplica`() = runBlocking {
        val id = c.repositorio.guardarMedicamento(metformina())
        val toma = Toma(id, t(21))

        c.repositorio.guardarTomas(listOf(toma))
        c.repositorio.guardarTomas(listOf(toma.copy(estado = Estado.TOMADA, tomadaEn = t(21, 12), avisoHijo = AvisoHijo.TOMADA)))

        val guardadas = c.repositorio.tomasRecientes(t(0))
        assertEquals(1, guardadas.size)
        assertEquals(t(21, 12), guardadas.single().tomadaEn)
        assertEquals(AvisoHijo.TOMADA, guardadas.single().avisoHijo)
    }

    @Test
    fun `las recientes incluyen la ultima de cada medicamento aunque sea antigua`() = runBlocking {
        val id = c.repositorio.guardarMedicamento(metformina())
        val antigua = Toma(id, t(9, dia = HOY.minusDays(20)), Estado.TOMADA, tomadaEn = t(9, dia = HOY.minusDays(20)))
        val masAntigua = Toma(id, t(9, dia = HOY.minusDays(21)), Estado.NO_TOMADA)
        c.repositorio.guardarTomas(listOf(masAntigua, antigua))

        assertEquals(listOf(antigua), c.repositorio.tomasRecientes(t(0)))
        assertEquals(antigua, c.repositorio.toma(ClaveToma(id, antigua.programada)))
    }

    @Test
    fun `el tema de ntfy se genera una vez y se mantiene`() {
        // Si cambiara al reiniciar la app, el hijo dejaría de recibir los avisos sin enterarse.
        val primero = Preferencias(app).actual().ntfyTema
        val segundo = Preferencias(app).actual().ntfyTema

        assertEquals(primero, segundo)
        assertTrue(primero.startsWith("pastillero-") && primero.length > 25, primero)
    }

    @Test
    fun `los ajustes cambiados sobreviven a reabrir la app`() {
        c.preferencias.cambiar { it.copy(paciente = "Papá Paco", avisos = it.avisos.copy(intervaloAvisos = Duration.ofMinutes(3))) }

        val reabierta = Preferencias(app).actual()

        assertEquals("Papá Paco", reabierta.paciente)
        assertEquals(Duration.ofMinutes(3), reabierta.avisos.intervaloAvisos)
    }

    @Test
    fun `el PIN se comprueba y no se guarda en claro`() {
        c.preferencias.ponerPin("4821")

        assertTrue(c.preferencias.pinCorrecto("4821"))
        assertFalse(c.preferencias.pinCorrecto("1234"))
        assertFalse(c.preferencias.actual().pinHash!!.contains("4821"))
    }
}
