package es.abpdev.pastillero.ntfy

import es.abpdev.pastillero.dominio.MensajeHijo
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * Publica en un tema de ntfy (https://ntfy.sh por defecto).
 *
 * Va como JSON y no con cabeceras porque las cabeceras HTTP no admiten «Papá» ni emojis.
 */
class NtfyCliente(private val servidor: String, private val timeoutMs: Int = 10_000) {
    /** @throws IOException si no llega: sin red, servidor caído o respuesta distinta de 2xx. */
    fun publicar(tema: String, mensaje: MensajeHijo) {
        val cuerpo = buildString {
            append("{\"topic\":").append(json(tema))
            append(",\"title\":").append(json(mensaje.titulo))
            append(",\"message\":").append(json(mensaje.cuerpo))
            append(",\"priority\":").append(mensaje.prioridad)
            append(",\"tags\":[").append(mensaje.etiquetas.joinToString(",") { json(it) }).append("]}")
        }.toByteArray(Charsets.UTF_8)

        val conexion = URI(servidor.trimEnd('/') + "/").toURL().openConnection() as HttpURLConnection
        try {
            conexion.requestMethod = "POST"
            conexion.connectTimeout = timeoutMs
            conexion.readTimeout = timeoutMs
            conexion.doOutput = true
            conexion.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conexion.setFixedLengthStreamingMode(cuerpo.size)
            conexion.outputStream.use { it.write(cuerpo) }
            val codigo = conexion.responseCode
            if (codigo !in 200..299) throw IOException("ntfy respondió $codigo")
        } finally {
            conexion.disconnect()
        }
    }

    private fun json(texto: String): String = buildString {
        append('"')
        for (c in texto) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}
