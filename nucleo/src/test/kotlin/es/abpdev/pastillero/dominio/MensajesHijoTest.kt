package es.abpdev.pastillero.dominio

import kotlin.test.Test
import kotlin.test.assertEquals

class MensajesHijoTest {

    @Test
    fun `sin confirmar`() {
        val mensaje = MensajesHijo.sinConfirmar("Papá", metformina(), Toma(1, t(21)), t(21, 30), ZONA)

        assertEquals(
            MensajeHijo(
                titulo = "Papá: Metformina sin confirmar",
                cuerpo = "Tocaba a las 21:00. A las 21:30 seguía sin marcar.",
                prioridad = 4,
                etiquetas = listOf("pill"),
            ),
            mensaje,
        )
    }

    @Test
    fun `sin confirmar despues de silenciar lo dice`() {
        val toma = Toma(1, t(21), Estado.SILENCIADA, silenciadaEn = t(21, 5))

        val mensaje = MensajesHijo.sinConfirmar("Papá", metformina(), toma, t(21, 30), ZONA)

        assertEquals("Tocaba a las 21:00. Silenció el aviso a las 21:05 y a las 21:30 seguía sin marcar.", mensaje.cuerpo)
    }

    @Test
    fun `tomada al final`() {
        val toma = Toma(1, t(21), Estado.TOMADA, tomadaEn = t(21, 50))

        val mensaje = MensajesHijo.tomada("Papá", metformina(), toma, ZONA)

        assertEquals(
            MensajeHijo("Papá: Metformina tomada", "A las 21:50 (tarde, 50 min).", 3, listOf("white_check_mark")),
            mensaje,
        )
    }
}
