package es.abpdev.pastillero.actualizacion

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * Lee la última release de un repo público de GitHub y descarga su APK. Sin token: el repo
 * es público y basta con mirar cada pocas horas (el límite anónimo es de 60 consultas por hora).
 */
class ClienteGithub(
    private val repo: String,
    private val api: String = "https://api.github.com",
    private val agente: String = "Pastillero",
    private val timeoutMs: Int = 20_000,
) {
    /** La última release con APK, o null si no hay ninguna válida. @throws IOException sin red o si GitHub falla. */
    fun ultima(): Publicada? {
        val conexion = abrir("${api.trimEnd('/')}/repos/$repo/releases/latest")
        conexion.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            return when (val codigo = conexion.responseCode) {
                200 -> leer(conexion.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
                404 -> null // todavía no hay ninguna release
                else -> throw IOException("GitHub respondió $codigo")
            }
        } finally {
            conexion.disconnect()
        }
    }

    /** Descarga [url] en [destino]. Si falla a medias, no deja el fichero. @throws IOException */
    fun descargar(url: String, destino: File) {
        val parcial = File(destino.parentFile, destino.name + ".parcial")
        try {
            val conexion = abrir(url) // GitHub redirige a su almacén: se sigue sola (https → https)
            try {
                val codigo = conexion.responseCode
                if (codigo !in 200..299) throw IOException("La descarga respondió $codigo")
                val esperado = conexion.contentLengthLong
                conexion.inputStream.use { entrada -> parcial.outputStream().use { entrada.copyTo(it) } }
                if (esperado >= 0 && parcial.length() != esperado) throw IOException("Descarga incompleta")
            } finally {
                conexion.disconnect()
            }
            destino.delete()
            if (!parcial.renameTo(destino)) throw IOException("No se pudo guardar $destino")
        } finally {
            parcial.delete()
        }
    }

    private fun abrir(url: String): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", agente) // GitHub rechaza peticiones sin él
        }

    /** Solo hacen falta dos campos del JSON de GitHub; con una expresión basta y no hay que traer un parser. */
    private fun leer(json: String): Publicada? {
        val etiqueta = ETIQUETA.find(json)?.groupValues?.get(1) ?: return null
        val version = Version.leer(etiqueta) ?: return null
        val apk = APK.find(json)?.groupValues?.get(1) ?: return null
        return Publicada(version, apk)
    }

    private companion object {
        val ETIQUETA = Regex(""""tag_name"\s*:\s*"([^"]*)"""")
        val APK = Regex(""""browser_download_url"\s*:\s*"([^"]+\.apk)"""")
    }
}
