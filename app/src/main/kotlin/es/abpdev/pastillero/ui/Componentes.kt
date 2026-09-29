package es.abpdev.pastillero.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.abpdev.pastillero.dominio.Confirmacion
import es.abpdev.pastillero.dominio.Gesto

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

/**
 * Una toma abierta (sonando, silenciada o pospuesta), con sus opciones en grande y una debajo de otra.
 * La misma en la pantalla de alarma, en la principal y en la prueba: lo que ve es siempre igual.
 */
@Composable
fun TarjetaSonando(
    nombre: String,
    detalle: String,
    opciones: Set<Gesto>,
    minutosPosponer: Long,
    titulo: String = "Te toca ahora",
    activa: Boolean = true,
    pulsar: (Gesto) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Colores.tarjeta),
        border = BorderStroke(3.dp, Colores.ambar),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleLarge, color = Colores.ambar, fontWeight = FontWeight.Bold)
            Text(nombre, style = MaterialTheme.typography.displaySmall)
            if (detalle.isNotBlank()) Text(detalle, style = MaterialTheme.typography.titleLarge)
            if (Gesto.TOMADA in opciones) Button(
                onClick = { pulsar(Gesto.TOMADA) },
                enabled = activa,
                modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Colores.verde),
            ) { Text("YA ME LA HE TOMADO", style = TextoBotonGrande, textAlign = TextAlign.Center) }
            if (Gesto.POSPONER in opciones) {
                OutlinedButton(
                    onClick = { pulsar(Gesto.POSPONER) },
                    enabled = activa,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    border = BorderStroke(2.dp, Colores.ambar),
                ) { Text("Recuérdamelo en $minutosPosponer min", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center) }
            }
            if (Gesto.SILENCIAR in opciones) OutlinedButton(
                onClick = { pulsar(Gesto.SILENCIAR) },
                enabled = activa,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) { Text("Dejar de sonar", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

/** Lo que ha pasado al pulsar, en grande: que no le quede la duda de si ha funcionado. */
@Composable
fun PanelConfirmacion(confirmacion: Confirmacion, cerrar: () -> Unit) {
    val (fondo, tinta, icono) = when (confirmacion.tipo) {
        Confirmacion.Tipo.BIEN -> Triple(Colores.verdeClaro, Colores.verde, "✓")
        Confirmacion.Tipo.LUEGO -> Triple(Colores.ambarClaro, Colores.ambar, "⏰")
        Confirmacion.Tipo.APAGADA -> Triple(Colores.fondo, Colores.tintaSuave, "🔕")
        Confirmacion.Tipo.AVISO -> Triple(Colores.rojoClaro, Colores.rojo, "!")
    }
    Card(colors = CardDefaults.cardColors(containerColor = fondo), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(28.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(icono, fontSize = 80.sp, color = tinta)
            Text(
                confirmacion.titulo.removePrefix("✓ "),
                style = MaterialTheme.typography.headlineMedium,
                color = tinta,
                textAlign = TextAlign.Center,
            )
            Text(confirmacion.detalle, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Button(
                onClick = cerrar,
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                colors = ButtonDefaults.buttonColors(containerColor = tinta),
            ) { Text("Vale", style = TextoBotonGrande) }
        }
    }
}
