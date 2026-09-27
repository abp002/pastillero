package es.abpdev.pastillero.dominio

import java.time.Instant
import java.time.ZoneId

/** Lo que le llega al hijo por ntfy. Prioridad de ntfy: 3 normal, 4 alta. */
data class MensajeHijo(val titulo: String, val cuerpo: String, val prioridad: Int, val etiquetas: List<String>)

object MensajesHijo {
    /** [detectadoEn]: cuándo se vio que seguía sin marcar (el mensaje puede llegar más tarde si no hay red). */
    fun sinConfirmar(paciente: String, med: Medicamento, toma: Toma, detectadoEn: Instant, zona: ZoneId): MensajeHijo {
        val tocaba = Textos.hora(toma.programada, zona)
        val visto = Textos.hora(detectadoEn, zona)
        val cuerpo = toma.silenciadaEn
            ?.let { "Tocaba a las $tocaba. Silenció el aviso a las ${Textos.hora(it, zona)} y a las $visto seguía sin marcar." }
            ?: "Tocaba a las $tocaba. A las $visto seguía sin marcar."
        return MensajeHijo("$paciente: ${med.nombre} sin confirmar", cuerpo, prioridad = 4, etiquetas = listOf("pill"))
    }

    fun tomada(paciente: String, med: Medicamento, toma: Toma, zona: ZoneId): MensajeHijo {
        val tomadaEn = requireNotNull(toma.tomadaEn) { "La toma no está marcada: $toma" }
        val detalle = Textos.puntualidad(toma.programada, tomadaEn, med.margen)
        return MensajeHijo(
            "$paciente: ${med.nombre} tomada",
            "A las ${Textos.hora(tomadaEn, zona)} ($detalle).",
            prioridad = 3,
            etiquetas = listOf("white_check_mark"),
        )
    }

    fun prueba(paciente: String): MensajeHijo = MensajeHijo(
        "Prueba de Pastillero",
        "Si ves esto, los avisos de las pastillas de $paciente te llegarán aquí.",
        prioridad = 3,
        etiquetas = listOf("pill"),
    )
}
