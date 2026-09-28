package es.abpdev.pastillero.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.abpdev.pastillero.dominio.Boton
import es.abpdev.pastillero.dominio.Confirmacion
import es.abpdev.pastillero.dominio.Estado
import es.abpdev.pastillero.dominio.Fila
import es.abpdev.pastillero.dominio.Gesto
import es.abpdev.pastillero.dominio.Textos
import es.abpdev.pastillero.sistema.Permiso
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

@Composable
fun PantallaHoy(estado: EstadoApp, arreglar: (Permiso) -> Unit, irA: (Pantalla) -> Unit) {
    val hoy by estado.hoy.collectAsStateWithLifecycle()
    val faltan by estado.faltan.collectAsStateWithLifecycle()
    val ajustes by estado.ajustes.collectAsStateWithLifecycle()
    val confirmacion by estado.confirmacion.collectAsStateWithLifecycle()
    var confirmar by remember { mutableStateOf<Fila?>(null) }
    val barra = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { estado.mensajes.collect { barra.showSnackbar(it) } }

    Scaffold(containerColor = Colores.fondo, snackbarHost = { SnackbarHost(barra) }) { relleno ->
        val actual = hoy
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = relleno.calculateTopPadding() + 16.dp,
                bottom = relleno.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("Hoy", style = MaterialTheme.typography.displaySmall)
                if (actual != null) {
                    Text(Formato.fechaLarga(actual.ahora, actual.zona), style = MaterialTheme.typography.titleLarge, color = Colores.tintaSuave)
                }
            }
            if (faltan.isNotEmpty()) item { AvisoPermisos(faltan, arreglar) }
            if (actual != null && actual.filas.isEmpty()) {
                item {
                    Text(
                        "Todavía no hay pastillas apuntadas. Entra en Ajustes para añadirlas.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (actual != null) {
                // Lo que suena ahora va arriba y con sus tres opciones: es lo único que importa en ese momento.
                val (sonando, resto) = actual.filas.partition { it.sonando }
                items(sonando, key = { "sonando-${it.medicamento.id}-${it.programada.toEpochMilli()}" }) { fila ->
                    TarjetaSonando(
                        nombre = fila.medicamento.nombre,
                        detalle = listOf(Formato.diaYHora(fila.programada, actual.ahora, actual.zona), fila.medicamento.indicacion)
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        puedePosponer = (fila.toma?.posposiciones ?: 0) < ajustes.avisos.maxPosposiciones,
                        minutosPosponer = ajustes.avisos.posponer.toMinutes(),
                    ) { gesto -> estado.pulsar(fila, gesto) }
                }
                items(resto, key = { "${it.medicamento.id}-${it.programada.toEpochMilli()}" }) { fila ->
                    TarjetaToma(fila, actual.ahora, actual.zona) {
                        if (estado.pideConfirmacion(fila)) confirmar = fila else estado.pulsar(fila, Gesto.TOMADA)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { irA(Pantalla.HISTORIAL) }) { Text("Historial") }
                    TextButton(onClick = { irA(Pantalla.AJUSTES) }) { Text("Ajustes") }
                }
            }
        }
    }

    ConfirmacionGrande(confirmacion, estado::cerrarConfirmacion)

    confirmar?.let { fila ->
        val zona = hoy?.zona ?: ZoneId.systemDefault()
        AlertDialog(
            onDismissRequest = { confirmar = null },
            title = { Text("¿Ya te la has tomado?") },
            text = {
                Text(
                    "Tu hora para ${fila.medicamento.nombre} es a las ${Textos.hora(fila.programada, zona)}. " +
                        "Púlsalo solo si ya te la has tomado.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                Button(onClick = {
                    estado.pulsar(fila, Gesto.TOMADA)
                    confirmar = null
                }) { Text("Sí, ya me la he tomado") }
            },
            dismissButton = { TextButton(onClick = { confirmar = null }) { Text("No") } },
        )
    }
}

@Composable
private fun ConfirmacionGrande(confirmacion: Confirmacion?, cerrar: () -> Unit) {
    if (confirmacion == null) return
    // Se va sola a los pocos segundos; «Vale» la quita antes.
    LaunchedEffect(confirmacion) {
        delay(5_000)
        cerrar()
    }
    Dialog(onDismissRequest = cerrar) { PanelConfirmacion(confirmacion, cerrar) }
}

@Composable
private fun TarjetaToma(fila: Fila, ahora: Instant, zona: ZoneId, tomar: () -> Unit) {
    val toma = fila.toma
    val (fondo, tinta) = when {
        fila.boton == Boton.TOMADA -> Colores.ambarClaro to Colores.ambar
        toma?.estado == Estado.TOMADA -> Colores.verdeClaro to Colores.verde
        toma?.estado == Estado.NO_TOMADA -> Colores.rojoClaro to Colores.rojo
        else -> Colores.tarjeta to Colores.tintaSuave
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = fondo),
        border = if (fondo == Colores.tarjeta) BorderStroke(1.dp, Color(0xFFE3DDD3)) else null,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(fila.medicamento.nombre, style = MaterialTheme.typography.headlineMedium)
            Text(
                listOf(Formato.diaYHora(fila.programada, ahora, zona), fila.medicamento.indicacion)
                    .filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(textoEstado(fila, ahora, zona), style = MaterialTheme.typography.titleMedium, color = tinta, fontWeight = FontWeight.SemiBold)
            when (fila.boton) {
                Boton.TOMADA -> {
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = tomar,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Colores.verde),
                    ) { Text("YA ME LA HE TOMADO", style = TextoBotonGrande) }
                }
                Boton.ADELANTAR -> {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = tomar, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                        Text("Ya me la he tomado")
                    }
                }
                Boton.NINGUNO -> Unit
            }
        }
    }
}

private fun textoEstado(fila: Fila, ahora: Instant, zona: ZoneId): String {
    val toma = fila.toma ?: return "Próxima"
    val pospuesta = toma.pospuestaHasta?.takeIf { it > ahora }
    return when (toma.estado) {
        Estado.PENDIENTE -> when {
            pospuesta != null -> "Te lo recuerdo a las ${Textos.hora(pospuesta, zona)}"
            fila.boton == Boton.TOMADA -> "Te toca ahora"
            else -> "Sin marcar"
        }
        Estado.SILENCIADA -> if (fila.boton == Boton.TOMADA) "Silenciada. ¿Te la has tomado?" else Textos.estado(toma, fila.medicamento.margen, zona)
        Estado.TOMADA -> "✓ " + Textos.estado(toma, fila.medicamento.margen, zona)
        Estado.NO_TOMADA -> "✗ " + Textos.estado(toma, fila.medicamento.margen, zona).replaceFirstChar { it.uppercase() }
    }
}

@Composable
fun AvisoPermisos(faltan: List<Permiso>, arreglar: (Permiso) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Colores.rojoClaro)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Así no puede avisarte bien", style = MaterialTheme.typography.headlineSmall, color = Colores.rojo)
            for (permiso in faltan) {
                Text("${permiso.titulo}: ${permiso.porQue}", style = MaterialTheme.typography.bodyLarge)
                Button(
                    onClick = { arreglar(permiso) },
                    colors = ButtonDefaults.buttonColors(containerColor = Colores.rojo),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Activar ${permiso.titulo.lowercase()}") }
            }
        }
    }
}
