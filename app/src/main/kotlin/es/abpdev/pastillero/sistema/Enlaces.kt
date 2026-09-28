package es.abpdev.pastillero.sistema

import android.net.Uri
import androidx.core.net.toUri
import es.abpdev.pastillero.dominio.ClaveToma
import es.abpdev.pastillero.dominio.Gesto
import java.time.Instant

/**
 * Cómo viaja una pulsación en un Intent: el gesto en la acción y la toma en la URI.
 *
 * Cada toma lleva su propia URI: sin ella, los PendingIntent de dos tomas con el mismo gesto
 * serían «iguales» para Android y el segundo pisaría al primero (marcaría la otra pastilla).
 */
object Enlaces {
    fun accion(gesto: Gesto) = "es.abpdev.pastillero.${gesto.name}"

    fun gesto(accion: String?): Gesto? = Gesto.entries.find { accion(it) == accion }

    fun uri(clave: ClaveToma): Uri = "pastillero://toma/${clave.medicamentoId}/${clave.programada.toEpochMilli()}".toUri()

    fun clave(uri: Uri): ClaveToma? {
        val partes = uri.pathSegments
        if (uri.scheme != "pastillero" || uri.host != "toma" || partes.size != 2) return null
        val medicamento = partes[0].toLongOrNull() ?: return null
        val programada = partes[1].toLongOrNull() ?: return null
        return ClaveToma(medicamento, Instant.ofEpochMilli(programada))
    }
}
