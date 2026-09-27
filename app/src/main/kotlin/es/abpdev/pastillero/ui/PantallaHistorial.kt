package es.abpdev.pastillero.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.abpdev.pastillero.dominio.Estado
import es.abpdev.pastillero.dominio.Puntualidad
import es.abpdev.pastillero.dominio.Textos
import java.time.Duration

@Composable
fun PantallaHistorial(estado: EstadoApp, volver: () -> Unit) {
    val historial by estado.historial.collectAsStateWithLifecycle()

    Scaffold(containerColor = Colores.fondo) { relleno ->
        val h = historial
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = relleno.calculateTopPadding() + 8.dp,
                bottom = relleno.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Cabecera("Historial", volver) }
            if (h == null) return@LazyColumn
            item { Resumen(h) }
            h.tomas.groupBy { it.second.programada.atZone(h.zona).toLocalDate() }.forEach { (dia, tomas) ->
                item(key = dia.toString()) {
                    Text(Formato.fechaLarga(dia), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                }
                items(tomas, key = { (_, t) -> "${t.medicamentoId}-${t.programada.toEpochMilli()}" }) { (med, toma) ->
                    val color = when (toma.estado) {
                        Estado.TOMADA -> Colores.verde
                        Estado.NO_TOMADA -> Colores.rojo
                        else -> Colores.ambar
                    }
                    Column {
                        Text("${med.nombre} · ${Textos.hora(toma.programada, h.zona)}", style = MaterialTheme.typography.bodyLarge)
                        Text(Textos.estado(toma, med.margen, h.zona), style = MaterialTheme.typography.bodyMedium, color = color)
                    }
                }
            }
        }
    }
}

/** Una línea para el hijo: cómo han ido los últimos 7 días. */
@Composable
private fun Resumen(h: EstadoHistorial) {
    val semana = h.tomas.filter { (_, t) -> t.programada >= h.ahora.minus(Duration.ofDays(7)) && !t.abierta }
    val puntualidades = semana.filter { it.second.estado == Estado.TOMADA }
        .map { (med, t) -> Textos.clasificar(t.programada, t.tomadaEn!!, med.margen).puntualidad }
    val partes = listOf(
        puntualidades.count { it == Puntualidad.A_TIEMPO } to "a tiempo",
        puntualidades.count { it == Puntualidad.TARDE } to "tarde",
        puntualidades.count { it == Puntualidad.ADELANTADA } to "adelantadas",
        semana.count { it.second.estado == Estado.NO_TOMADA } to "sin tomar",
    ).filter { it.first > 0 }
    val texto = if (partes.isEmpty()) "Todavía no hay tomas esta semana." else partes.joinToString(" · ") { "${it.first} ${it.second}" }
    Seccion("Últimos 7 días") { Text(texto, style = MaterialTheme.typography.bodyLarge) }
}
