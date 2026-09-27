package es.abpdev.pastillero.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.abpdev.pastillero.sistema.Permiso

enum class Pantalla { HOY, HISTORIAL, AJUSTES, EDITOR }

class MainActivity : ComponentActivity() {
    private val estado: EstadoApp by viewModels { EstadoApp.Factory }

    private val pedirNotificaciones = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        // Si ya lo denegó para siempre, Android ni pregunta: queda el ajuste del sistema.
        if (!concedido) abrir(estado.ajustePara(Permiso.NOTIFICACIONES))
        estado.alVolver()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TemaPastillero { App(estado, ::arreglar) } }
        if (savedInstanceState == null && faltaPermisoNotificaciones()) {
            pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        estado.alVolver()
    }

    private fun arreglar(permiso: Permiso) {
        if (permiso == Permiso.NOTIFICACIONES && faltaPermisoNotificaciones()) {
            pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            abrir(estado.ajustePara(permiso))
        }
    }

    private fun faltaPermisoNotificaciones() = Build.VERSION.SDK_INT >= 33 &&
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** Algunos fabricantes quitan pantallas de ajustes: entonces, la ficha de la app. */
    private fun abrir(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
        }
    }
}

@Composable
private fun App(estado: EstadoApp, arreglar: (Permiso) -> Unit) {
    var pantalla by rememberSaveable { mutableStateOf(Pantalla.HOY) }
    var editando by rememberSaveable { mutableLongStateOf(0L) }
    var desbloqueado by rememberSaveable { mutableStateOf(false) }
    val ajustes by estado.ajustes.collectAsStateWithLifecycle()

    fun ir(destino: Pantalla) {
        if (destino == Pantalla.HOY) desbloqueado = false // el PIN se pide cada vez que se entra
        pantalla = destino
    }

    BackHandler(enabled = pantalla != Pantalla.HOY) {
        ir(if (pantalla == Pantalla.EDITOR) Pantalla.AJUSTES else Pantalla.HOY)
    }

    when (pantalla) {
        Pantalla.HOY -> PantallaHoy(estado, arreglar, ::ir)
        Pantalla.HISTORIAL -> PantallaHistorial(estado) { ir(Pantalla.HOY) }
        Pantalla.AJUSTES ->
            if (ajustes.pinHash != null && !desbloqueado) {
                PantallaHoy(estado, arreglar, ::ir)
                PedirPin(estado, entrar = { desbloqueado = true }, cancelar = { ir(Pantalla.HOY) })
            } else {
                PantallaAjustes(estado, arreglar, editar = { id ->
                    editando = id
                    ir(Pantalla.EDITOR)
                }, volver = { ir(Pantalla.HOY) })
            }
        Pantalla.EDITOR -> EditorMedicamento(estado, editando) { ir(Pantalla.AJUSTES) }
    }
}
