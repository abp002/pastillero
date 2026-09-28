package es.abpdev.pastillero

import es.abpdev.pastillero.datos.AjustesApp
import es.abpdev.pastillero.datos.Repositorio
import es.abpdev.pastillero.dominio.Accion
import es.abpdev.pastillero.dominio.AvisoHijo
import es.abpdev.pastillero.dominio.ClaveToma
import es.abpdev.pastillero.dominio.Gesto
import es.abpdev.pastillero.dominio.Medicamento
import es.abpdev.pastillero.dominio.MensajesHijo
import es.abpdev.pastillero.dominio.Operaciones
import es.abpdev.pastillero.dominio.Planificador
import es.abpdev.pastillero.dominio.Resultado
import es.abpdev.pastillero.dominio.Toma
import es.abpdev.pastillero.sistema.ColaAvisosHijo
import es.abpdev.pastillero.sistema.Despertador
import es.abpdev.pastillero.sistema.Notificador
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

fun interface Reloj {
    fun ahora(): Instant
}

/**
 * Une el dominio con Android: lee el estado, pasa el [Planificador], ejecuta lo que decide y
 * programa la única alarma. Todo va bajo un cerrojo: la notificación y la pantalla pueden
 * marcar la misma toma a la vez y solo una de las dos la apunta (criterio 4).
 */
class Revisor(
    private val repositorio: Repositorio,
    private val notificador: Notificador,
    private val despertador: Despertador,
    private val avisosHijo: ColaAvisosHijo,
    private val reloj: Reloj,
    private val zona: () -> ZoneId,
) {
    private val cerrojo = Mutex()

    suspend fun revisar() = cerrojo.withLock { revisarYa(reloj.ahora()) }

    suspend fun marcarTomada(clave: ClaveToma): Resultado = operar { ahora, ajustes ->
        repositorio.toma(clave)?.let { Operaciones.marcarTomada(it, ahora, ajustes.avisos) }
    }

    suspend fun silenciar(clave: ClaveToma): Resultado = operar { ahora, _ ->
        repositorio.toma(clave)?.let { Operaciones.silenciar(it, ahora) }
    }

    suspend fun posponer(clave: ClaveToma): Resultado = operar { ahora, ajustes ->
        repositorio.toma(clave)?.let { Operaciones.posponer(it, ahora, ajustes.avisos) }
    }

    /** Lo que se pulsa sobre una toma que suena, venga de la notificación o de la pantalla. */
    suspend fun pulsar(gesto: Gesto, clave: ClaveToma): Resultado = when (gesto) {
        Gesto.TOMADA -> marcarTomada(clave)
        Gesto.POSPONER -> posponer(clave)
        Gesto.SILENCIAR -> silenciar(clave)
    }

    suspend fun adelantar(medicamentoId: Long): Resultado = operar { ahora, _ ->
        repositorio.medicamentos().find { it.id == medicamentoId }
            ?.let { Operaciones.adelantar(it, repositorio.ultima(medicamentoId), ahora, zona()) }
    }

    /** Pone el estado al día, aplica la pulsación y vuelve a revisar, todo sin soltar el cerrojo. */
    private suspend fun operar(operacion: suspend (Instant, AjustesApp) -> Resultado?): Resultado = cerrojo.withLock {
        val ahora = reloj.ahora()
        revisarYa(ahora)
        val ajustes = repositorio.ajustes()
        val resultado = operacion(ahora, ajustes) ?: Resultado.NoPermitido("Esa toma no existe")
        if (resultado is Resultado.Hecho) {
            ejecutar(resultado.acciones, listOf(resultado.toma), ahora, repositorio.medicamentos(), ajustes)
            repositorio.guardarTomas(listOf(resultado.toma))
            revisarYa(ahora)
        }
        resultado
    }

    private suspend fun revisarYa(ahora: Instant) {
        val ajustes = repositorio.ajustes()
        val medicamentos = repositorio.medicamentos()
        val tomas = repositorio.tomasRecientes(ahora.minus(HISTORIA))
        val plan = Planificador.planificar(ahora, medicamentos, tomas, ajustes.avisos, zona())
        // Primero se avisa y después se guarda: si el proceso muere en medio, se repite el aviso
        // (la cola del hijo no duplica) en vez de perderlo.
        ejecutar(plan.acciones, tomas + plan.tomas, ahora, medicamentos, ajustes)
        repositorio.guardarTomas(plan.tomas)
        despertador.programar(plan.proximaRevision)
    }

    private fun ejecutar(
        acciones: List<Accion>,
        tomas: List<Toma>,
        ahora: Instant,
        medicamentos: List<Medicamento>,
        ajustes: AjustesApp,
    ) {
        val porClave = tomas.associateBy { it.clave } // las posteriores (ya actualizadas) ganan
        val porId = medicamentos.associateBy { it.id }
        for (accion in acciones) {
            if (accion is Accion.Retirar) {
                notificador.retirar(accion.toma)
                continue
            }
            val med = porId[accion.toma.medicamentoId] ?: continue
            when (accion) {
                is Accion.Sonar ->
                    notificador.sonar(med, accion.toma, accion.nivel, accion.puedePosponer, ajustes.avisos.posponer)
                is Accion.AvisarHijo -> {
                    val toma = porClave[accion.toma] ?: continue
                    val mensaje = when (accion.tipo) {
                        AvisoHijo.SIN_CONFIRMAR -> MensajesHijo.sinConfirmar(ajustes.paciente, med, toma, ahora, zona())
                        AvisoHijo.TOMADA -> MensajesHijo.tomada(ajustes.paciente, med, toma, zona())
                        AvisoHijo.NINGUNO -> continue
                    }
                    avisosHijo.encolar(accion.toma, accion.tipo, mensaje)
                }
                is Accion.Retirar -> Unit
            }
        }
    }

    private companion object {
        /** Lo que se relee de la BD en cada revisión (más la última de cada medicamento). */
        val HISTORIA: Duration = Duration.ofDays(8)
    }
}
