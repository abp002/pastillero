package es.abpdev.pastillero.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.abpdev.pastillero.Contenedor
import es.abpdev.pastillero.contenedor
import es.abpdev.pastillero.dominio.Estado
import es.abpdev.pastillero.dominio.Medicamento
import es.abpdev.pastillero.dominio.Textos
import es.abpdev.pastillero.dominio.Toma
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration

/** La alarma a pantalla completa: sale encima del bloqueo y se cierra sola cuando no queda nada sonando. */
class AlarmaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mostrarSobreElBloqueo()
        val prueba = intent.getBooleanExtra(PRUEBA, false)
        setContent { TemaPastillero { PantallaAlarma(contenedor, prueba, cerrar = ::finish) } }
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
private fun PantallaAlarma(c: Contenedor, prueba: Boolean, cerrar: () -> Unit) {
    val alcance = rememberCoroutineScope()
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
    LaunchedEffect(sonando) { if (!prueba && sonando != null && sonando.isEmpty()) cerrar() }

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
            Text(
                "Esto es una prueba. Así sonará cuando lleve un rato sin marcar la pastilla, aunque el móvil esté en silencio.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = {
                c.notificador.retirarPrueba()
                cerrar()
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) { Text("Cerrar", style = TextoBotonGrande) }
        }
        for ((med, toma) in sonando.orEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Colores.tarjeta)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(med.nombre, style = MaterialTheme.typography.displaySmall)
                    Text(
                        listOf(Textos.hora(toma.programada, c.zona()), med.indicacion).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Button(
                        onClick = { alcance.launch { c.revisor.marcarTomada(toma.clave) } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Colores.verde),
                    ) { Text("YA ME LA HE TOMADO", style = TextoBotonGrande, textAlign = TextAlign.Center) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (toma.posposiciones < ajustes.avisos.maxPosposiciones) {
                            OutlinedButton(
                                onClick = { alcance.launch { c.revisor.posponer(toma.clave) } },
                                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                            ) { Text("Posponer ${ajustes.avisos.posponer.toMinutes()} min", textAlign = TextAlign.Center) }
                        }
                        OutlinedButton(
                            onClick = { alcance.launch { c.revisor.silenciar(toma.clave) } },
                            modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                        ) { Text("Silenciar") }
                    }
                }
            }
        }
    }
}
