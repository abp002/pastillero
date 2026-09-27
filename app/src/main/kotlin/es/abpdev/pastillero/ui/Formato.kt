package es.abpdev.pastillero.ui

import es.abpdev.pastillero.dominio.Textos
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Fechas como se dicen: «Hoy 21:00», «Mañana 9:00», «lunes, 28 de septiembre». */
object Formato {
    private val espanol: Locale = Locale.forLanguageTag("es-ES")
    private val largo = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", espanol)
    private val corto = DateTimeFormatter.ofPattern("EEE d", espanol)

    fun fechaLarga(instante: Instant, zona: ZoneId): String = instante.atZone(zona).format(largo)

    fun fechaLarga(dia: LocalDate): String = dia.format(largo).replaceFirstChar { it.uppercase() }

    fun diaYHora(instante: Instant, ahora: Instant, zona: ZoneId): String {
        val dia = instante.atZone(zona).toLocalDate()
        val hoy = ahora.atZone(zona).toLocalDate()
        val hora = Textos.hora(instante, zona)
        return when (dia) {
            hoy -> "Hoy $hora"
            hoy.plusDays(1) -> "Mañana $hora"
            hoy.minusDays(1) -> "Ayer $hora"
            else -> "${dia.format(corto)} $hora"
        }
    }
}
