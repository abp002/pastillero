package es.abpdev.pastillero.dominio

import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/** Cómo se reparten las tomas de un medicamento. */
sealed interface Pauta {
    /** Siempre a las mismas horas del reloj: un retraso no mueve las siguientes. */
    data class HorasFijas(val horas: List<LocalTime>) : Pauta {
        init {
            require(horas.isNotEmpty()) { "Una pauta a horas fijas necesita al menos una hora" }
        }
    }

    /** Cada cierto tiempo desde la toma real; si no se marca, desde la hora que tocaba. */
    data class CadaIntervalo(val cada: Duration, val primera: Instant) : Pauta {
        init {
            require(!cada.isNegative && !cada.isZero) { "El intervalo tiene que ser positivo" }
        }
    }
}

data class Medicamento(
    val id: Long,
    val nombre: String,
    val indicacion: String,
    val pauta: Pauta,
    /** Diferencia con la hora programada que todavía cuenta como «a tiempo». La fija su médico. */
    val margen: Duration,
    /** Alta del medicamento: las tomas anteriores no existen y no suenan. */
    val desde: Instant,
    val activo: Boolean = true,
)

enum class Estado { PENDIENTE, TOMADA, SILENCIADA, NO_TOMADA }

/** Lo último que se le ha avisado al hijo sobre una toma. */
enum class AvisoHijo { NINGUNO, SIN_CONFIRMAR, TOMADA }

/** Una toma se identifica por su medicamento y la hora a la que tocaba: marcarla dos veces es marcar la misma. */
data class ClaveToma(val medicamentoId: Long, val programada: Instant)

data class Toma(
    val medicamentoId: Long,
    val programada: Instant,
    val estado: Estado = Estado.PENDIENTE,
    val tomadaEn: Instant? = null,
    val silenciadaEn: Instant? = null,
    val pospuestaHasta: Instant? = null,
    val posposiciones: Int = 0,
    val ultimoAviso: Instant? = null,
    val avisoHijo: AvisoHijo = AvisoHijo.NINGUNO,
) {
    val clave: ClaveToma get() = ClaveToma(medicamentoId, programada)

    /** Sin resolver: todavía puede sonar (si está pendiente) o marcarse como tomada. */
    val abierta: Boolean get() = estado == Estado.PENDIENTE || estado == Estado.SILENCIADA
}

data class Ajustes(
    val intervaloAvisos: Duration = Duration.ofMinutes(5),
    val alarmaTras: Duration = Duration.ofMinutes(15),
    val posponer: Duration = Duration.ofMinutes(10),
    val maxPosposiciones: Int = 2,
    val avisoHijoTras: Duration = Duration.ofMinutes(30),
    val avisoHijoActivo: Boolean = true,
)

enum class Nivel { AVISO, ALARMA }

/** Lo que el sistema tiene que hacer ahora, fuera del dominio. */
sealed interface Accion {
    val toma: ClaveToma

    data class Sonar(override val toma: ClaveToma, val nivel: Nivel, val puedePosponer: Boolean) : Accion

    /** Quitar la notificación de esa toma: ya está resuelta o cerrada. */
    data class Retirar(override val toma: ClaveToma) : Accion

    data class AvisarHijo(override val toma: ClaveToma, val tipo: AvisoHijo) : Accion
}

/**
 * Resultado de revisar el estado a una hora dada.
 *
 * @property tomas las tomas creadas o modificadas, para guardarlas.
 * @property proximaRevision cuándo hay que volver a revisar: la única alarma que se programa.
 */
data class Plan(val tomas: List<Toma>, val acciones: List<Accion>, val proximaRevision: Instant?)

sealed interface Resultado {
    data class Hecho(val toma: Toma, val acciones: List<Accion>) : Resultado

    /** Ya estaba marcada: no se apunta otra toma (evita la doble dosis por doble pulsación). */
    data class YaTomada(val tomadaEn: Instant) : Resultado

    data class NoPermitido(val motivo: String) : Resultado
}
