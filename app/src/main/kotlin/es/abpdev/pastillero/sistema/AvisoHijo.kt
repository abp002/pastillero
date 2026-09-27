package es.abpdev.pastillero.sistema

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import es.abpdev.pastillero.contenedor
import es.abpdev.pastillero.dominio.AvisoHijo
import es.abpdev.pastillero.dominio.ClaveToma
import es.abpdev.pastillero.dominio.MensajeHijo
import es.abpdev.pastillero.ntfy.NtfyCliente
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cola de avisos al hijo. WorkManager los guarda aunque se reinicie el móvil y los reintenta
 * hasta que haya red. Cada toma tiene su cadena: «tomada» siempre llega después de «sin confirmar».
 */
class ColaAvisosHijo(private val context: Context) {
    fun encolar(clave: ClaveToma, tipo: AvisoHijo, mensaje: MensajeHijo) {
        val trabajo = OneTimeWorkRequestBuilder<AvisoHijoWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(AvisoHijoWorker.datos(mensaje))
            .build()
        // Si el «sin confirmar» ya está en cola, no se duplica; «tomada» se encadena detrás.
        val politica = if (tipo == AvisoHijo.TOMADA) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(nombre(clave), politica, trabajo)
    }

    companion object {
        fun nombre(clave: ClaveToma) = "hijo-${clave.medicamentoId}-${clave.programada.toEpochMilli()}"
    }
}

class AvisoHijoWorker(context: Context, parametros: WorkerParameters) : CoroutineWorker(context, parametros) {
    override suspend fun doWork(): Result {
        val ajustes = applicationContext.contenedor.preferencias.actual()
        // Si lo apagó mientras esperaba red, ya no se manda.
        if (!ajustes.avisos.avisoHijoActivo) return Result.success()
        val mensaje = mensaje(inputData) ?: return Result.failure()
        return try {
            withContext(Dispatchers.IO) { NtfyCliente(ajustes.ntfyServidor).publicar(ajustes.ntfyTema, mensaje) }
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        }
    }

    companion object {
        private const val TITULO = "titulo"
        private const val CUERPO = "cuerpo"
        private const val PRIORIDAD = "prioridad"
        private const val ETIQUETAS = "etiquetas"

        fun datos(m: MensajeHijo): Data = workDataOf(
            TITULO to m.titulo,
            CUERPO to m.cuerpo,
            PRIORIDAD to m.prioridad,
            ETIQUETAS to m.etiquetas.toTypedArray(),
        )

        fun mensaje(datos: Data): MensajeHijo? {
            val titulo = datos.getString(TITULO) ?: return null
            val cuerpo = datos.getString(CUERPO) ?: return null
            return MensajeHijo(titulo, cuerpo, datos.getInt(PRIORIDAD, 3), datos.getNullableStringArray(ETIQUETAS)?.filterNotNull().orEmpty())
        }
    }
}
