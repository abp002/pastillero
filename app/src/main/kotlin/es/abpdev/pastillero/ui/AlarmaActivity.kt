package es.abpdev.pastillero.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.abpdev.pastillero.Contenedor
import es.abpdev.pastillero.contenedor
import es.abpdev.pastillero.dominio.ClaveToma
import es.abpdev.pastillero.dominio.Confirmacion
import es.abpdev.pastillero.dominio.Confirmaciones
import es.abpdev.pastillero.dominio.Estado
import es.abpdev.pastillero.dominio.Gesto
import es.abpdev.pastillero.dominio.Medicamento
import es.abpdev.pastillero.dominio.Operaciones
import es.abpdev.pastillero.dominio.Resultado
import es.abpdev.pastillero.dominio.Textos
import es.abpdev.pastillero.dominio.Toma
import es.abpdev.pastillero.sistema.Enlaces
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * La pantalla de la pastilla: sale sola a su hora (encima del bloqueo), al tocar la notificación
 * y al pulsar sus botones. Hace el gesto, lo confirma en grande y se cierra cuando no queda nada sonando.
 */
class AlarmaActivity : ComponentActivity() {
    private val confirmacion = MutableStateFlow<Confirmacion?>(null)
    private val pulsando = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mostrarSobreElBloqueo()
        val prueba = intent.getBooleanExtra(PRUEBA, false)
        // Al girar la pantalla no se repite el gesto que trajo la notificación.
        if (savedInstanceState == null) atender(intent)
        setContent {
            TemaPastillero {
                PantallaAlarma(
                    c = contenedor,
                    prueba = prueba,
                    confirmacion = confirmacion.collectAsStateWithLifecycle().value,
                    pulsando = pulsando.collectAsStateWithLifecycle().value,
                    pulsar = ::pulsar,
                    pulsarPrueba = ::pulsarPrueba,
                    cerrarConfirmacion = { confirmacion.value = null },
                    cerrar = ::finish,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        atender(intent)
    }

    /** Un botón de la notificación trae el gesto en la acción y la toma en la URI. */
    private fun atender(intent: Intent) {
        val gesto = Enlaces.gesto(intent.action) ?: return
        val clave = intent.data?.let(Enlaces::clave) ?: return
        pulsar(gesto, clave)
    }

    private fun pulsar(gesto: Gesto, clave: ClaveToma) {
        val c = contenedor
        pulsando.value = true
        // En el ámbito de la app y no en el de la pantalla: se apunta aunque la pantalla se cierre.
        c.ambito.launch {
            val resultado = c.revisor.pulsar(gesto, clave)
            val nombre = c.repositorio.medicamentos().find { it.id == clave.medicamentoId }?.nombre ?: "La pastilla"
            confirmacion.value = Confirmaciones.de(gesto, resultado, nombre, c.zona())
            pulsando.value = false
        }
    }

    /** En la prueba no se apunta nada: se enseña lo que verá. */
    private fun pulsarPrueba(gesto: Gesto) {
        val c = contenedor
        val ahora = c.reloj.ahora()
        val simulada = Toma(0, ahora, Estado.TOMADA, tomadaEn = ahora, pospuestaHasta = ahora.plus(c.preferencias.actual().avisos.posponer))
        confirmacion.value = Confirmaciones.de(gesto, Resultado.Hecho(simulada, emptyList()), "Pastilla de prueba", c.zona())
    }

    private fun mostrarSobreElBloqueo() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        const val PRUEBA = "prueba"
    }
}

@Composable
private fun PantallaAlarma(
    c: Contenedor,
    prueba: Boolean,
    confirmacion: Confirmacion?,
    pulsando: Boolean,
    pulsar: (Gesto, ClaveToma) -> Unit,
    pulsarPrueba: (Gesto) -> Unit,
    cerrarConfirmacion: () -> Unit,
    cerrar: () -> Unit,
) {
    val desde = remember { c.reloj.ahora().minus(Duration.ofDays(2)) }
    val tomas by remember { c.repositorio.observarTomas(desde) }.collectAsStateWithLifecycle(initialValue = null)
    val medicamentos by remember { c.repositorio.observarMedicamentos() }.collectAsStateWithLifecycle(initialValue = null)
    val ajustes by c.preferencias.ajustes.collectAsStateWithLifecycle()
    val ahora by produceState(c.reloj.ahora()) {
        while (true) {
            delay(10_000)
            value = c.reloj.ahora()
        }
    }

    val cargadas = tomas
    val meds = medicamentos
    val sonando: List<Pair<Medicamento, Toma>>? = if (cargadas == null || meds == null) {
        null
    } else {
        cargadas.filter { it.estado == Estado.PENDIENTE && it.programada <= ahora && (it.pospuestaHasta?.let { p -> p <= ahora } ?: true) }
            .mapNotNull { t -> meds.find { it.id == t.medicamentoId }?.let { it to t } }
    }

    // Tras la confirmación: la siguiente que suene o, en la prueba, se acabó.
    fun terminarConfirmacion() {
        cerrarConfirmacion()
        if (prueba) {
            c.notificador.retirarPrueba()
            cerrar()
        }
    }
    // La confirmación se queda unos segundos y después se va sola.
    LaunchedEffect(confirmacion) {
        if (confirmacion != null) {
            delay(4_000)
            terminarConfirmacion()
        }
    }
    // Sin nada sonando ni nada que confirmar, la pantalla sobra.
    LaunchedEffect(sonando, confirmacion, pulsando) {
        if (pulsando || confirmacion != null) return@LaunchedEffect
        if (prueba) return@LaunchedEffect
        if (sonando != null && sonando.isEmpty()) cerrar()
    }

    if (confirmacion != null) {
        // Sola y en el centro: es lo único que tiene que leer en ese momento.
        Box(
            Modifier.fillMaxSize().background(Colores.ambarClaro).safeDrawingPadding().padding(20.dp),
            contentAlignment = Alignment.Center,
        ) { PanelConfirmacion(confirmacion, ::terminarConfirmacion) }
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Colores.ambarClaro)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Hora de la pastilla", style = MaterialTheme.typography.displaySmall, color = Colores.ambar)
        if (prueba) {
            TarjetaSonando(
                nombre = "Pastilla de prueba",
                detalle = "Así sonará y se verá a su hora",
                opciones = Gesto.entries.toSet(),
                minutosPosponer = ajustes.avisos.posponer.toMinutes(),
                pulsar = pulsarPrueba,
            )
        }
        for ((med, toma) in sonando.orEmpty()) {
            TarjetaSonando(
                nombre = med.nombre,
                detalle = listOf(Textos.hora(toma.programada, c.zona()), med.indicacion).filter { it.isNotBlank() }.joinToString(" · "),
                opciones = Operaciones.opciones(toma, ahora, ajustes.avisos),
                minutosPosponer = ajustes.avisos.posponer.toMinutes(),
                activa = !pulsando,
            ) { gesto -> pulsar(gesto, toma.clave) }
        }
    }
}
