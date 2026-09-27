package es.abpdev.pastillero.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.abpdev.pastillero.dominio.Medicamento
import es.abpdev.pastillero.dominio.Pauta
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** [id] 0 para una pastilla nueva. */
@Composable
fun EditorMedicamento(estado: EstadoApp, id: Long, cerrar: () -> Unit) {
    val medicamentos by estado.medicamentos.collectAsStateWithLifecycle()
    val original = medicamentos.find { it.id == id }
    if (id != 0L && original == null) return // todavía cargando
    key(original?.id) { Formulario(estado, original, cerrar) }
}

private enum class Tipo { FIJAS, INTERVALO }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Formulario(estado: EstadoApp, original: Medicamento?, cerrar: () -> Unit) {
    val zona = ZoneId.systemDefault()
    val fijas = original?.pauta as? Pauta.HorasFijas
    val intervalo = original?.pauta as? Pauta.CadaIntervalo
    var nombre by remember { mutableStateOf(original?.nombre.orEmpty()) }
    var indicacion by remember { mutableStateOf(original?.indicacion.orEmpty()) }
    var tipo by remember { mutableStateOf(if (intervalo != null) Tipo.INTERVALO else Tipo.FIJAS) }
    var horas by remember { mutableStateOf(fijas?.horas?.sorted() ?: listOf(LocalTime.of(9, 0))) }
    var cadaHoras by remember { mutableLongStateOf(intervalo?.cada?.toHours() ?: 8L) }
    var primera by remember { mutableStateOf(intervalo?.primera?.atZone(zona)?.toLocalTime() ?: LocalTime.of(9, 0)) }
    var margen by remember { mutableLongStateOf(original?.margen?.toMinutes() ?: 30L) }
    var eligiendo by remember { mutableStateOf<((LocalTime) -> Unit)?>(null) }
    var eligiendoDesde by remember { mutableStateOf(LocalTime.of(9, 0)) }

    val error = when {
        nombre.isBlank() -> "Falta el nombre"
        tipo == Tipo.FIJAS && horas.isEmpty() -> "Falta al menos una hora"
        else -> null
    }

    fun guardar() {
        val pauta = when (tipo) {
            Tipo.FIJAS -> Pauta.HorasFijas(horas.distinct().sorted())
            Tipo.INTERVALO -> {
                // Si no ha tocado la primera toma, se conserva la original para no mover la cadena.
                val sinCambios = intervalo?.primera?.atZone(zona)?.toLocalTime() == primera
                val inicio = if (sinCambios) intervalo.primera else LocalDate.now(zona).atTime(primera).atZone(zona).toInstant()
                Pauta.CadaIntervalo(Duration.ofHours(cadaHoras), inicio)
            }
        }
        val base = original ?: Medicamento(0, "", "", pauta, Duration.ofMinutes(margen), Instant.EPOCH)
        estado.guardarMedicamento(
            base.copy(
                nombre = nombre.trim(),
                indicacion = indicacion.trim(),
                pauta = pauta,
                margen = Duration.ofMinutes(margen),
                activo = true,
            ),
        )
        cerrar()
    }

    Scaffold(containerColor = Colores.fondo) { relleno ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(relleno)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Cabecera(if (original == null) "Nueva pastilla" else original.nombre, cerrar)

            Seccion("Qué") {
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    indicacion,
                    { indicacion = it },
                    label = { Text("Cómo (p. ej. 1 pastilla con la cena)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Seccion("Cuándo") {
                for ((opcion, texto) in listOf(Tipo.FIJAS to "A horas fijas", Tipo.INTERVALO to "Cada cierto tiempo desde la última")) {
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = tipo == opcion, onClick = { tipo = opcion }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = tipo == opcion, onClick = { tipo = opcion })
                        Text(texto, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                when (tipo) {
                    Tipo.FIJAS -> {
                        Text("Si se retrasa, la siguiente no se mueve.", style = MaterialTheme.typography.bodyMedium, color = Colores.tintaSuave)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (hora in horas) {
                                AssistChip(onClick = { horas = horas - hora }, label = { Text("$hora  ✕") })
                            }
                        }
                        OutlinedButton(onClick = {
                            eligiendoDesde = horas.lastOrNull()?.plusHours(12) ?: LocalTime.of(9, 0)
                            eligiendo = { h -> horas = (horas + h).distinct().sorted() }
                        }) { Text("Añadir hora") }
                    }
                    Tipo.INTERVALO -> {
                        Text(
                            "La siguiente se cuenta desde la hora a la que la marque.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Colores.tintaSuave,
                        )
                        Numero("Cada", cadaHoras, 1L..48L, "h") { cadaHoras = it }
                        OutlinedButton(onClick = {
                            eligiendoDesde = primera
                            eligiendo = { h -> primera = h }
                        }) { Text("Primera toma: $primera") }
                    }
                }
            }

            Seccion("Margen «a tiempo»") {
                Text("Lo que diga su médico: cuánto puede separarse de la hora sin que cuente como tarde.",
                    style = MaterialTheme.typography.bodyMedium, color = Colores.tintaSuave)
                Numero("Margen", margen, 5L..240L, "min") { margen = it }
            }

            error?.let { Text(it, color = Colores.rojo, style = MaterialTheme.typography.bodyLarge) }
            Button(onClick = ::guardar, enabled = error == null, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
            if (original != null) {
                TextButton(onClick = {
                    estado.guardarMedicamento(original.copy(activo = false))
                    cerrar()
                }) { Text("Ya no la toma: quitarla", color = Colores.rojo) }
            }
        }
    }

    eligiendo?.let { alElegir ->
        ElegirHora(eligiendoDesde, elegir = {
            alElegir(it)
            eligiendo = null
        }, cancelar = { eligiendo = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ElegirHora(inicial: LocalTime, elegir: (LocalTime) -> Unit, cancelar: () -> Unit) {
    val reloj = rememberTimePickerState(inicial.hour, inicial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = cancelar,
        text = { TimePicker(state = reloj) },
        confirmButton = { Button(onClick = { elegir(LocalTime.of(reloj.hour, reloj.minute)) }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = cancelar) { Text("Cancelar") } },
    )
}
