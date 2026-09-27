package es.abpdev.pastillero.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Cabecera de las pantallas secundarias: volver bien grande, que se vea. */
@Composable
fun Cabecera(titulo: String, volver: () -> Unit) {
    Column {
        TextButton(onClick = volver) { Text("‹ Volver") }
        Text(titulo, style = MaterialTheme.typography.displaySmall)
    }
}

@Composable
fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Colores.tarjeta), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titulo, style = MaterialTheme.typography.headlineSmall)
            contenido()
        }
    }
}

/** Un número con − y +, sin teclado: más difícil equivocarse. */
@Composable
fun Numero(etiqueta: String, valor: Long, rango: LongRange, unidad: String, cambiar: (Long) -> Unit) {
    Column {
        Text(etiqueta, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { cambiar((valor - 1).coerceIn(rango)) }, enabled = valor > rango.first) { Text("−") }
            Text("$valor $unidad", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 96.dp))
            OutlinedButton(onClick = { cambiar((valor + 1).coerceIn(rango)) }, enabled = valor < rango.last) { Text("+") }
        }
    }
}
