package es.abpdev.pastillero.datos

import android.content.Context
import androidx.core.content.edit
import es.abpdev.pastillero.dominio.Ajustes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration

data class AjustesApp(
    val avisos: Ajustes = Ajustes(),
    /** Cómo se le nombra en los mensajes al familiar: «Papá: <pastilla> sin confirmar». */
    val paciente: String = "Papá",
    val ntfyServidor: String = "https://ntfy.sh",
    /** Tema aleatorio: en ntfy.sh quien conoce el tema puede leerlo, así que hace de contraseña. */
    val ntfyTema: String,
    /** Hash del PIN que protege los ajustes; null si no hay PIN. */
    val pinHash: String? = null,
)

/** Ajustes en SharedPreferences, observables para la interfaz. */
class Preferencias(context: Context) {
    private val prefs = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
    private val estado = MutableStateFlow(leer())

    val ajustes: StateFlow<AjustesApp> = estado.asStateFlow()

    fun actual(): AjustesApp = estado.value

    @Synchronized
    fun cambiar(cambio: (AjustesApp) -> AjustesApp) {
        val nuevo = cambio(estado.value)
        escribir(nuevo)
        estado.value = nuevo
    }

    fun ponerPin(pin: String?) = cambiar { it.copy(pinHash = pin?.let(::hashPin)) }

    fun pinCorrecto(pin: String): Boolean = actual().pinHash?.let { it == hashPin(pin) } ?: true

    private fun hashPin(pin: String): String {
        val sal = prefs.getString(SAL, null) ?: aleatorio(16).also { prefs.edit(commit = true) { putString(SAL, it) } }
        return MessageDigest.getInstance("SHA-256").digest((sal + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun leer(): AjustesApp {
        val tema = prefs.getString(TEMA, null) ?: "pastillero-${aleatorio(20)}".also {
            prefs.edit(commit = true) { putString(TEMA, it) }
        }
        val defecto = Ajustes()
        return AjustesApp(
            avisos = Ajustes(
                intervaloAvisos = minutos(INTERVALO, defecto.intervaloAvisos),
                // Un solo sonido desde el primer aviso: el de la alarma, a pantalla completa.
                alarmaTras = Duration.ZERO,
                posponer = minutos(POSPONER, defecto.posponer),
                maxPosposiciones = prefs.getInt(MAX_POSPOSICIONES, defecto.maxPosposiciones),
                avisoHijoTras = minutos(HIJO_TRAS, defecto.avisoHijoTras),
                avisoHijoActivo = prefs.getBoolean(HIJO_ACTIVO, defecto.avisoHijoActivo),
            ),
            paciente = prefs.getString(PACIENTE, null) ?: "Papá",
            ntfyServidor = prefs.getString(SERVIDOR, null) ?: "https://ntfy.sh",
            ntfyTema = tema,
            pinHash = prefs.getString(PIN, null),
        )
    }

    private fun escribir(a: AjustesApp) = prefs.edit(commit = true) {
        putLong(INTERVALO, a.avisos.intervaloAvisos.toMinutes())
        putLong(POSPONER, a.avisos.posponer.toMinutes())
        putInt(MAX_POSPOSICIONES, a.avisos.maxPosposiciones)
        putLong(HIJO_TRAS, a.avisos.avisoHijoTras.toMinutes())
        putBoolean(HIJO_ACTIVO, a.avisos.avisoHijoActivo)
        putString(PACIENTE, a.paciente)
        putString(SERVIDOR, a.ntfyServidor)
        putString(TEMA, a.ntfyTema)
        putString(PIN, a.pinHash)
    }

    private fun minutos(clave: String, defecto: Duration): Duration =
        Duration.ofMinutes(prefs.getLong(clave, defecto.toMinutes()))

    companion object {
        private const val INTERVALO = "intervaloAvisos"
        private const val POSPONER = "posponer"
        private const val MAX_POSPOSICIONES = "maxPosposiciones"
        private const val HIJO_TRAS = "avisoHijoTras"
        private const val HIJO_ACTIVO = "avisoHijoActivo"
        private const val PACIENTE = "paciente"
        private const val SERVIDOR = "ntfyServidor"
        private const val TEMA = "ntfyTema"
        private const val PIN = "pin"
        private const val SAL = "sal"

        private const val ALFABETO = "abcdefghijkmnpqrstuvwxyz23456789"

        fun aleatorio(n: Int): String {
            val azar = SecureRandom()
            return (1..n).map { ALFABETO[azar.nextInt(ALFABETO.length)] }.joinToString("")
        }
    }
}
