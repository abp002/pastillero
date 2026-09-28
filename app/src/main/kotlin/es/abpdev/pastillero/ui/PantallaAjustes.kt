package es.abpdev.pastillero.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.abpdev.pastillero.dominio.Medicamento
import es.abpdev.pastillero.dominio.Pauta
import es.abpdev.pastillero.dominio.Textos
import es.abpdev.pastillero.sistema.Permiso
import java.time.Duration
import java.time.ZoneId

@Composable
fun PantallaAjustes(estado: EstadoApp, arreglar: (Permiso) -> Unit, editar: (Long) -> Unit, volver: () -> Unit) {
    val ajustes by estado.ajustes.collectAsStateWithLifecycle()
    val medicamentos by estado.medicamentos.collectAsStateWithLifecycle()
    val faltan by estado.faltan.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val barra = remember { SnackbarHostState() }
    var cambiarPin by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { estado.mensajes.collect { barra.showSnackbar(it) } }

    Scaffold(containerColor = Colores.fondo, snackbarHost = { SnackbarHost(barra) }) { relleno ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(relleno)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Cabecera("Ajustes", volver)

            Seccion("Pastillas") {
                val zona = ZoneId.systemDefault()
                for (med in medicamentos.filter { it.activo }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(med.nombre, style = MaterialTheme.typography.titleLarge)
                            Text(describirPauta(med, zona), style = MaterialTheme.typography.bodyMedium, color = Colores.tintaSuave)
                        }
                        OutlinedButton(onClick = { editar(med.id) }) { Text("Cambiar") }
                    }
                }
                Button(onClick = { editar(0L) }, modifier = Modifier.fillMaxWidth()) { Text("Añadir pastilla") }
            }

            Seccion("Cómo insiste") {
                val a = ajustes.avisos
                Numero("Repetir el aviso cada", a.intervaloAvisos.toMinutes(), 1L..30L, "min") { v ->
                    estado.cambiarAjustes { it.copy(avisos = it.avisos.copy(intervaloAvisos = Duration.ofMinutes(v))) }
                }
                Numero("Alarma a pantalla completa desde", a.alarmaTras.toMinutes(), 0L..120L, "min") { v ->
                    estado.cambiarAjustes { it.copy(avisos = it.avisos.copy(alarmaTras = Duration.ofMinutes(v))) }
                }
                Numero("Posponer", a.posponer.toMinutes(), 1L..60L, "min") { v ->
                    estado.cambiarAjustes { it.copy(avisos = it.avisos.copy(posponer = Duration.ofMinutes(v))) }
                }
                Numero("Veces que puede posponer", a.maxPosposiciones.toLong(), 0L..5L, "") { v ->
                    estado.cambiarAjustes { it.copy(avisos = it.avisos.copy(maxPosposiciones = v.toInt())) }
                }
                OutlinedButton(onClick = estado::probarAlarma, modifier = Modifier.fillMaxWidth()) { Text("Probar la alarma") }
            }

            Seccion("Avisarte a ti") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Avisarte si no la marca", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(
                        checked = ajustes.avisos.avisoHijoActivo,
                        onCheckedChange = { activo -> estado.cambiarAjustes { it.copy(avisos = it.avisos.copy(avisoHijoActivo = activo)) } },
                    )
                }
                Numero("Avisarte si pasan", ajustes.avisos.avisoHijoTras.toMinutes(), 5L..240L, "min") { v ->
                    estado.cambiarAjustes { it.copy(avisos = it.avisos.copy(avisoHijoTras = Duration.ofMinutes(v))) }
                }
                OutlinedTextField(
                    value = ajustes.paciente,
                    onValueChange = { nombre -> estado.cambiarAjustes { it.copy(paciente = nombre) } },
                    label = { Text("Cómo le llamas en el aviso") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Instala ntfy en tu móvil y suscríbete al tema «${ajustes.ntfyTema}» (servidor ${ajustes.ntfyServidor}).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = {
                    val texto = "Para recibir los avisos de las pastillas de ${ajustes.paciente}: instala ntfy y " +
                        "suscríbete al tema ${ajustes.ntfyTema}. ${ajustes.ntfyServidor.trimEnd('/')}/${ajustes.ntfyTema}"
                    contexto.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto), "Compartir"),
                    )
                }, modifier = Modifier.fillMaxWidth()) { Text("Compartir el tema") }
                OutlinedButton(onClick = estado::enviarPrueba, modifier = Modifier.fillMaxWidth()) { Text("Enviar un aviso de prueba") }
            }

            Seccion("Versión") {
                val version = remember { contexto.packageManager.getPackageInfo(contexto.packageName, 0).versionName.orEmpty() }
                Text("Mis pastillas $version. Se actualiza sola cuando publicas una versión nueva.", style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = estado::buscarActualizacion, modifier = Modifier.fillMaxWidth()) {
                    Text("Buscar actualización ahora")
                }
            }

            Seccion("PIN de los ajustes") {
                Text(
                    if (ajustes.pinHash == null) "Sin PIN: cualquiera puede cambiar las horas." else "Con PIN.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(onClick = { cambiarPin = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (ajustes.pinHash == null) "Poner PIN" else "Cambiar PIN")
                }
                if (ajustes.pinHash != null) {
                    TextButton(onClick = { estado.ponerPin(null) }) { Text("Quitar PIN") }
                }
            }

            if (faltan.isNotEmpty()) AvisoPermisos(faltan, arreglar)
        }
    }

    if (cambiarPin) {
        NuevoPin(
            guardar = {
                estado.ponerPin(it)
                cambiarPin = false
            },
            cancelar = { cambiarPin = false },
        )
    }
}

fun describirPauta(med: Medicamento, zona: ZoneId): String {
    val pauta = when (val p = med.pauta) {
        is Pauta.HorasFijas -> p.horas.sorted().joinToString(" y ") { it.toString() }
        is Pauta.CadaIntervalo -> "Cada ${Textos.duracion(p.cada)} desde las ${Textos.hora(p.primera, zona)}"
    }
    return "$pauta · margen ${Textos.duracion(med.margen)}"
}

@Composable
fun PedirPin(estado: EstadoApp, entrar: () -> Unit, cancelar: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = cancelar,
        title = { Text("PIN de los ajustes") },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin = it.filter(Char::isDigit).take(8)
                    error = false
                },
                isError = error,
                supportingText = { if (error) Text("PIN incorrecto") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { if (estado.pinCorrecto(pin)) entrar() else error = true }) { Text("Entrar") }
        },
        dismissButton = { TextButton(onClick = cancelar) { Text("Cancelar") } },
    )
}

@Composable
private fun NuevoPin(guardar: (String) -> Unit, cancelar: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var repetido by remember { mutableStateOf("") }
    val valido = pin.length >= 4 && pin == repetido
    AlertDialog(
        onDismissRequest = cancelar,
        title = { Text("Nuevo PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((valor, cambiar, etiqueta) in listOf(
                    Triple(pin, { v: String -> pin = v }, "PIN (4 cifras o más)"),
                    Triple(repetido, { v: String -> repetido = v }, "Repítelo"),
                )) {
                    OutlinedTextField(
                        value = valor,
                        onValueChange = { cambiar(it.filter(Char::isDigit).take(8)) },
                        label = { Text(etiqueta) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { guardar(pin) }, enabled = valido) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = cancelar) { Text("Cancelar") } },
    )
}
