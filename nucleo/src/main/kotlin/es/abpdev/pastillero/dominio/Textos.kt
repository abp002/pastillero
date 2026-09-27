package es.abpdev.pastillero.dominio

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class Puntualidad { A_TIEMPO, TARDE, ADELANTADA }

data class Clasificacion(val puntualidad: Puntualidad, val diferencia: Duration)

/** Cómo se le cuenta cada toma a una persona. */
object Textos {
    private val FORMATO_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** El margen incluye el borde: 30 min tarde con 30 de margen es «a tiempo». */
    fun clasificar(programada: Instant, tomadaEn: Instant, margen: Duration): Clasificacion {
        val diferencia = Duration.between(programada, tomadaEn)
        val puntualidad = when {
            diferencia.abs() <= margen -> Puntualidad.A_TIEMPO
            diferencia.isNegative -> Puntualidad.ADELANTADA
            else -> Puntualidad.TARDE
        }
        return Clasificacion(puntualidad, diferencia.abs())
    }

    /** «40 min», «1 h 30 min», «2 h». */
    fun duracion(d: Duration): String {
        val minutos = d.toMinutes()
        val horas = minutos / 60
        val resto = minutos % 60
        return when {
            horas == 0L -> "$resto min"
            resto == 0L -> "$horas h"
            else -> "$horas h $resto min"
        }
    }

    /** «21:05». */
    fun hora(instante: Instant, zona: ZoneId): String = instante.atZone(zona).format(FORMATO_HORA)

    /** «a tiempo», «tarde, 40 min», «adelantada, 1 h 30 min». */
    fun puntualidad(programada: Instant, tomadaEn: Instant, margen: Duration): String {
        val c = clasificar(programada, tomadaEn, margen)
        return when (c.puntualidad) {
            Puntualidad.A_TIEMPO -> "a tiempo"
            Puntualidad.TARDE -> "tarde, ${duracion(c.diferencia)}"
            Puntualidad.ADELANTADA -> "adelantada, ${duracion(c.diferencia)}"
        }
    }

    /** «21:12 · a tiempo», «21:40 · tarde, 40 min», «sin marcar», «no tomada»… */
    fun estado(toma: Toma, margen: Duration, zona: ZoneId): String = when (toma.estado) {
        Estado.PENDIENTE -> "sin marcar"
        Estado.SILENCIADA -> "silenciada a las ${hora(toma.silenciadaEn!!, zona)}, sin marcar"
        Estado.NO_TOMADA -> toma.silenciadaEn?.let { "silenciada a las ${hora(it, zona)}, no tomada" } ?: "no tomada"
        Estado.TOMADA -> "${hora(toma.tomadaEn!!, zona)} · ${puntualidad(toma.programada, toma.tomadaEn, margen)}"
    }
}
