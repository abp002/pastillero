package es.abpdev.pastillero.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.abpdev.pastillero.Contenedor
import es.abpdev.pastillero.PastilleroApp
import es.abpdev.pastillero.datos.AjustesApp
import es.abpdev.pastillero.dominio.Boton
import es.abpdev.pastillero.dominio.Confirmacion
import es.abpdev.pastillero.dominio.Confirmaciones
import es.abpdev.pastillero.dominio.Fila
import es.abpdev.pastillero.dominio.Gesto
import es.abpdev.pastillero.dominio.Medicamento
import es.abpdev.pastillero.dominio.MensajesHijo
import es.abpdev.pastillero.dominio.Operaciones
import es.abpdev.pastillero.dominio.Resultado
import es.abpdev.pastillero.dominio.Toma
import es.abpdev.pastillero.dominio.VistaDelDia
import es.abpdev.pastillero.ntfy.NtfyCliente
import es.abpdev.pastillero.sistema.Permiso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

data class EstadoHoy(val ahora: Instant, val zona: ZoneId, val filas: List<Fila>)

data class EstadoHistorial(val ahora: Instant, val zona: ZoneId, val tomas: List<Pair<Medicamento, Toma>>)

class EstadoApp(private val c: Contenedor) : ViewModel() {
    /** Cada 20 s se recalcula la pantalla: «Te toca ahora» tiene que aparecer sin tocar nada. */
    private val tic = flow {
        while (true) {
            emit(c.reloj.ahora())
            delay(20_000)
        }
    }

    private val medicamentosFlujo = c.repositorio.observarMedicamentos()

    val hoy: StateFlow<EstadoHoy?> =
        combine(medicamentosFlujo, c.repositorio.observarTomas(c.reloj.ahora().minus(Duration.ofDays(3))), tic) { meds, tomas, ahora ->
            EstadoHoy(ahora, c.zona(), VistaDelDia.filas(ahora, meds, tomas, c.zona()))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val historial: StateFlow<EstadoHistorial?> =
        combine(medicamentosFlujo, c.repositorio.observarTomas(c.reloj.ahora().minus(Duration.ofDays(30))), tic) { meds, tomas, ahora ->
            val porId = meds.associateBy { it.id }
            val pasadas = tomas.filter { it.programada <= ahora || it.tomadaEn != null }
                .mapNotNull { t -> porId[t.medicamentoId]?.let { it to t } }
                .sortedByDescending { it.second.programada }
            EstadoHistorial(ahora, c.zona(), pasadas)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val medicamentos: StateFlow<List<Medicamento>> =
        medicamentosFlujo.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ajustes: StateFlow<AjustesApp> = c.preferencias.ajustes

    private val faltanFlujo = MutableStateFlow(c.permisos.faltan())
    val faltan: StateFlow<List<Permiso>> = faltanFlujo.asStateFlow()

    private val avisos = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Mensajes cortos para la barra de abajo. */
    val mensajes: SharedFlow<String> = avisos

    fun alVolver() {
        faltanFlujo.value = c.permisos.faltan()
        c.autoactualizacion.programar()
        viewModelScope.launch { c.revisor.revisar() }
    }

    fun buscarActualizacion() {
        c.autoactualizacion.buscarYa()
        avisos.tryEmit("Buscando. Si hay una versión nueva, se instalará sola en un momento.")
    }

    fun ajustePara(permiso: Permiso) = c.permisos.ajuste(permiso)

    /** Si antes de marcarla hay que preguntar «¿Seguro?» (criterio 3). */
    fun pideConfirmacion(fila: Fila): Boolean =
        fila.boton == Boton.ADELANTAR && Operaciones.pideConfirmacion(fila.medicamento, fila.programada, c.reloj.ahora())

    private val confirmacionFlujo = MutableStateFlow<Confirmacion?>(null)

    /** Lo que ha pasado al pulsar, para enseñarlo en grande. */
    val confirmacion: StateFlow<Confirmacion?> = confirmacionFlujo.asStateFlow()

    fun cerrarConfirmacion() {
        confirmacionFlujo.value = null
    }

    /** Un botón de la pantalla principal: la que suena, la silenciada o la siguiente antes de hora. */
    fun pulsar(fila: Fila, gesto: Gesto) = viewModelScope.launch {
        val toma = fila.toma
        val resultado = when {
            toma != null && fila.boton == Boton.TOMADA -> c.revisor.pulsar(gesto, toma.clave)
            gesto == Gesto.TOMADA -> c.revisor.adelantar(fila.medicamento.id)
            else -> Resultado.NoPermitido("Esa toma todavía no ha llegado")
        }
        confirmacionFlujo.value = Confirmaciones.de(gesto, resultado, fila.medicamento.nombre, c.zona())
    }

    fun guardarMedicamento(med: Medicamento) = viewModelScope.launch {
        c.repositorio.guardarMedicamento(if (med.id == 0L) med.copy(desde = c.reloj.ahora()) else med)
        c.revisor.revisar()
    }

    fun cambiarAjustes(cambio: (AjustesApp) -> AjustesApp) {
        c.preferencias.cambiar(cambio)
        viewModelScope.launch { c.revisor.revisar() }
    }

    fun ponerPin(pin: String?) = c.preferencias.ponerPin(pin)

    fun pinCorrecto(pin: String) = c.preferencias.pinCorrecto(pin)

    fun enviarPrueba() = viewModelScope.launch {
        val a = c.preferencias.actual()
        val texto = try {
            withContext(Dispatchers.IO) { NtfyCliente(a.ntfyServidor).publicar(a.ntfyTema, MensajesHijo.prueba(a.paciente)) }
            "Prueba enviada. Mira si te ha llegado."
        } catch (e: IOException) {
            "No se pudo enviar: ${e.message ?: "sin conexión"}"
        }
        avisos.emit(texto)
    }

    /** A los 10 s, para que dé tiempo a bloquear el móvil y verla como la verá él. */
    fun probarAlarma() {
        avisos.tryEmit("Sonará en 10 segundos. Bloquea el móvil para verla como la verá él.")
        c.ambito.launch {
            delay(10_000)
            c.notificador.probar()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { EstadoApp((this[APPLICATION_KEY] as PastilleroApp).contenedor) }
        }
    }
}
