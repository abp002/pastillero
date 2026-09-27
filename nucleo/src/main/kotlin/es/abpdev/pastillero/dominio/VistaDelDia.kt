package es.abpdev.pastillero.dominio

import java.time.Instant
import java.time.ZoneId

/** El botón que lleva cada fila de la pantalla principal. */
enum class Boton {
    NINGUNO,

    /** Marca la toma abierta (sonando o silenciada). */
    TOMADA,

    /** Marca la siguiente antes de su hora. */
    ADELANTAR,
}

/**
 * Una fila de la pantalla «Hoy».
 *
 * @property toma null cuando todavía no ha llegado su hora y nadie la ha marcado.
 */
data class Fila(val medicamento: Medicamento, val programada: Instant, val toma: Toma?, val boton: Boton)

object VistaDelDia {
    /**
     * Las tomas de hoy, la última de cada medicamento aunque sea de anoche («¿me la tomé?»),
     * la que siga abierta y la siguiente. Cada medicamento lleva como mucho un botón:
     * o la abierta o la siguiente, nunca las dos.
     */
    fun filas(ahora: Instant, medicamentos: List<Medicamento>, tomas: List<Toma>, zona: ZoneId): List<Fila> {
        val hoy = ahora.atZone(zona).toLocalDate()
        val filas = mutableListOf<Fila>()
        for (med in medicamentos.filter { it.activo }) {
            val propias = tomas.filter { it.medicamentoId == med.id }.sortedBy { it.programada }
            val ultima = propias.lastOrNull()
            val ultimaPasada = propias.lastOrNull { it.programada <= ahora }
            var hayAbierta = false
            for (toma in propias) {
                val abierta = toma.abierta && ahora < Calendario.cierre(med, toma, zona)
                hayAbierta = hayAbierta || abierta
                val visible = abierta || toma == ultimaPasada || toma.programada > ahora ||
                    toma.programada.atZone(zona).toLocalDate() == hoy
                if (visible) filas += Fila(med, toma.programada, toma, if (abierta) Boton.TOMADA else Boton.NINGUNO)
            }
            // Si ya adelantó la siguiente, esa es la siguiente: no se enseña otra detrás.
            if (ultima == null || ultima.programada <= ahora) {
                val adelantable = !hayAbierta && Operaciones.puedeAdelantar(med, ultima, ahora, zona)
                filas += Fila(
                    med,
                    Calendario.siguiente(med, ultima, zona),
                    null,
                    if (adelantable) Boton.ADELANTAR else Boton.NINGUNO,
                )
            }
        }
        return filas.sortedWith(compareBy<Fila>({ it.programada }, { it.medicamento.id }))
    }
}
