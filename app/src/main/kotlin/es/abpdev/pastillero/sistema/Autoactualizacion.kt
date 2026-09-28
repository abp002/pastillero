package es.abpdev.pastillero.sistema

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import es.abpdev.pastillero.actualizacion.Actualizador
import es.abpdev.pastillero.contenedor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * La app se actualiza sola desde las releases de GitHub, sin que quien la usa toque nada
 * (Android 12+). Android solo acepta una actualización firmada con la misma clave: nadie más
 * puede colar una versión.
 */
class Autoactualizacion(private val context: Context) {

    /** Cada 6 h. Programarla otra vez no la duplica. */
    fun programar() {
        val trabajo = PeriodicWorkRequestBuilder<ActualizacionWorker>(6, TimeUnit.HOURS)
            .setConstraints(condiciones())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODICA, ExistingPeriodicWorkPolicy.KEEP, trabajo)
    }

    /** El botón «Buscar actualización» de Ajustes. */
    fun buscarYa() {
        val trabajo = OneTimeWorkRequestBuilder<ActualizacionWorker>().setConstraints(condiciones()).build()
        WorkManager.getInstance(context).enqueueUniqueWork(AHORA, ExistingWorkPolicy.REPLACE, trabajo)
    }

    private fun condiciones() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    companion object {
        const val REPO = "abp002/pastillero"
        private const val PERIODICA = "actualizar"
        private const val AHORA = "actualizar-ya"
    }
}

class ActualizacionWorker(context: Context, parametros: WorkerParameters) : CoroutineWorker(context, parametros) {
    override suspend fun doWork(): Result {
        val c = applicationContext.contenedor
        val instalada = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName.orEmpty()
        val publicada = try {
            withContext(Dispatchers.IO) { c.github.ultima() }
        } catch (e: IOException) {
            Log.i(ETIQUETA, "Sin respuesta de GitHub, se reintenta: ${e.message}")
            return Result.retry()
        }
        val nueva = Actualizador.decidir(instalada, publicada) ?: return Result.success()

        // Actualizar reinicia la app: nunca con una toma sonando ni justo antes de una.
        val ahora = c.reloj.ahora()
        val tomas = c.repositorio.tomasRecientes(ahora.minus(Duration.ofDays(8)))
        if (!Actualizador.esBuenMomento(ahora, c.repositorio.medicamentos(), tomas, c.zona())) return Result.retry()
        // Sin el permiso de instalar, Android no deja hacerlo; el recuadro rojo de la app lo pide.
        if (!applicationContext.packageManager.canRequestPackageInstalls()) return Result.success()

        val apk = File(applicationContext.cacheDir, "actualizacion.apk")
        return try {
            withContext(Dispatchers.IO) {
                c.github.descargar(nueva.apk, apk)
                instalar(apk)
            }
            Log.i(ETIQUETA, "Instalando $instalada → ${nueva.version}")
            Result.success()
        } catch (e: IOException) {
            Log.i(ETIQUETA, "Descarga o instalación fallida, se reintenta: ${e.message}")
            Result.retry()
        }
    }

    private fun instalar(apk: File) {
        val instalador = applicationContext.packageManager.packageInstaller
        val parametros = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(applicationContext.packageName)
            // Una app que se actualiza a sí misma puede hacerlo sin preguntar desde Android 12.
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = instalador.createSession(parametros)
        instalador.openSession(id).use { sesion ->
            sesion.openWrite("pastillero.apk", 0, apk.length()).use { salida ->
                apk.inputStream().use { it.copyTo(salida) }
                sesion.fsync(salida)
            }
            // Mutable a propósito: el sistema añade el resultado al Intent.
            val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val resultado = PendingIntent.getBroadcast(
                applicationContext,
                id,
                Intent(applicationContext, InstalacionReceiver::class.java),
                mutable or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            sesion.commit(resultado.intentSender)
        }
    }

    private companion object {
        const val ETIQUETA = "Actualizacion"
    }
}

/** El resultado de la instalación. Si Android pide confirmación (anterior a 12), se le deja una notificación. */
class InstalacionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmar = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
                context.contenedor.notificador.avisarActualizacion(confirmar)
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // ya es la nueva: MY_PACKAGE_REPLACED reprograma las alarmas
            else -> Log.w("Actualizacion", "No se instaló: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
    }
}
