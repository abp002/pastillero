package es.abpdev.pastillero.sistema

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import es.abpdev.pastillero.Contenedor
import es.abpdev.pastillero.contenedor
import kotlinx.coroutines.launch

/** Salta la alarma programada: toca revisar. */
class AlarmaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = enSegundoPlano(context) { it.revisor.revisar() }
}

/** Tras reiniciar, actualizar la app o cambiar la hora, las alarmas se pierden o se desfasan: se reprograman. */
class ArranqueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in ACCIONES) enSegundoPlano(context) { it.revisor.revisar() }
    }

    companion object {
        /** La de las alarmas exactas es de Android 12: en versiones anteriores, simplemente no llega. */
        @SuppressLint("InlinedApi")
        val ACCIONES = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}

/** El receptor tiene unos segundos: se trabaja fuera del hilo principal y se avisa al acabar. */
private fun BroadcastReceiver.enSegundoPlano(context: Context, trabajo: suspend (Contenedor) -> Unit) {
    val pendiente: BroadcastReceiver.PendingResult? = goAsync()
    val contenedor = context.contenedor
    contenedor.ambito.launch {
        try {
            trabajo(contenedor)
        } finally {
            pendiente?.finish()
        }
    }
}
