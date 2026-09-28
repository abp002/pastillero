package es.abpdev.pastillero.actualizacion

import es.abpdev.pastillero.dominio.Calendario
import es.abpdev.pastillero.dominio.Medicamento
import es.abpdev.pastillero.dominio.Toma
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** X.Y.Z. Se compara por números: 1.10.0 es mayor que 1.9.9. */
data class Version(val mayor: Int, val menor: Int, val parche: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int =
        compareValuesBy(this, other, Version::mayor, Version::menor, Version::parche)

    override fun toString() = "$mayor.$menor.$parche"

    companion object {
        /** «v0.2.1» o «0.2.1»; cualquier otra cosa (betas, «latest»…) es null. */
        fun leer(texto: String): Version? {
            val partes = FORMATO.matchEntire(texto)?.groupValues?.drop(1) ?: return null
            val (mayor, menor, parche) = partes.map { it.toIntOrNull() ?: return null }
            return Version(mayor, menor, parche)
        }

        private val FORMATO = Regex("""v?(\d+)\.(\d+)\.(\d+)""")
    }
}

/** La última versión publicada y dónde está su APK. */
data class Publicada(val version: Version, val apk: String)

object Actualizador {
    /** No se actualiza con menos de esto hasta la siguiente toma: la app se reinicia al actualizarse. */
    val ANTELACION: Duration = Duration.ofMinutes(15)

    /** La versión que hay que descargar, o null si no hay nada mejor que [instalada]. */
    fun decidir(instalada: String, publicada: Publicada?): Publicada? {
        val actual = Version.leer(instalada) ?: return null // una versión de pruebas no se toca
        return publicada?.takeIf { it.version > actual }
    }

    /**
     * Si se puede instalar ya. Actualizar reinicia la app, así que no se hace con una toma
     * sonando o sin marcar, ni poco antes de la siguiente.
     */
    fun esBuenMomento(ahora: Instant, medicamentos: List<Medicamento>, tomas: List<Toma>, zona: ZoneId): Boolean {
        for (med in medicamentos.filter { it.activo }) {
            val propias = tomas.filter { it.medicamentoId == med.id }.sortedBy { it.programada }
            if (propias.any { it.abierta && ahora < Calendario.cierre(med, it, zona) }) return false
            // Incluye la que ya debería estar sonando y todavía no se ha revisado.
            if (Calendario.siguiente(med, propias.lastOrNull(), zona) <= ahora.plus(ANTELACION)) return false
        }
        return true
    }
}
