package es.abpdev.pastillero.dominio

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Cuándo toca cada toma. */
object Calendario {
    /** Una toma adelantada muy cerca de la anterior no puede dejar la siguiente antes que ella. */
    private val SEPARACION_MINIMA: Duration = Duration.ofMinutes(1)

    /**
     * La toma que sigue a [ultima], o la primera desde el alta si no hay ninguna.
     *
     * Con horas fijas, a la siguiente hora del reloj en [zona] (el cambio de hora no la mueve).
     * Con intervalo, [ultima] tomada cuenta desde la hora real; si no, desde la hora que tocaba.
     */
    fun siguiente(med: Medicamento, ultima: Toma?, zona: ZoneId): Instant = when (val pauta = med.pauta) {
        is Pauta.HorasFijas -> siguienteHora(pauta.horas, ultima?.programada ?: med.desde.minusNanos(1), zona)
        is Pauta.CadaIntervalo -> if (ultima == null) {
            primeraDesde(pauta, med.desde)
        } else {
            val base = if (ultima.estado == Estado.TOMADA) ultima.tomadaEn!! else ultima.programada
            maxOf(base.plus(pauta.cada), ultima.programada.plus(SEPARACION_MINIMA))
        }
    }

    /**
     * Hasta cuándo sigue abierta una toma sin marcar: a mitad de camino hasta la siguiente.
     *
     * Pasado ese punto, «me la he tomado» significa la siguiente adelantada, no esta con retraso.
     */
    fun cierre(med: Medicamento, toma: Toma, zona: ZoneId): Instant {
        val siguiente = siguiente(med, toma, zona)
        return toma.programada.plus(Duration.between(toma.programada, siguiente).dividedBy(2))
    }

    private fun siguienteHora(horas: List<LocalTime>, despuesDe: Instant, zona: ZoneId): Instant {
        val dia = despuesDe.atZone(zona).toLocalDate()
        val ordenadas = horas.sorted()
        // Con hoy y mañana basta; el tercer día cubre un día de cambio de hora sin esa hora.
        for (dias in 0L..2L) {
            for (hora in ordenadas) {
                val instante = ZonedDateTime.of(dia.plusDays(dias), hora, zona).toInstant()
                if (instante > despuesDe) return instante
            }
        }
        error("Pauta sin horas: $horas")
    }

    /** La primera toma a partir del alta: si el alta es posterior, salta de intervalo en intervalo. */
    private fun primeraDesde(pauta: Pauta.CadaIntervalo, desde: Instant): Instant {
        if (pauta.primera >= desde) return pauta.primera
        val cada = pauta.cada.toMillis()
        val saltos = (Duration.between(pauta.primera, desde).toMillis() + cada - 1) / cada
        return pauta.primera.plusMillis(saltos * cada)
    }
}
