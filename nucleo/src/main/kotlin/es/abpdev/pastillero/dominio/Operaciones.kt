package es.abpdev.pastillero.dominio

import java.time.Instant
import java.time.ZoneId

/**
 * Lo que puede pulsar quien toma las pastillas. Todas son idempotentes.
 *
 * Se aplican sobre el estado ya revisado: la app pasa el [Planificador] antes de cada pulsación,
 * así una toma que ya debía estar cerrada llega aquí cerrada.
 */
object Operaciones {
    fun marcarTomada(toma: Toma, ahora: Instant, ajustes: Ajustes): Resultado = when (toma.estado) {
        Estado.TOMADA -> Resultado.YaTomada(toma.tomadaEn!!)
        Estado.NO_TOMADA -> Resultado.NoPermitido("Esa toma ya se cerró")
        Estado.PENDIENTE, Estado.SILENCIADA -> {
            val avisarHijo = toma.avisoHijo == AvisoHijo.SIN_CONFIRMAR && ajustes.avisoHijoActivo
            val tomada = toma.copy(
                estado = Estado.TOMADA,
                tomadaEn = ahora,
                avisoHijo = if (avisarHijo) AvisoHijo.TOMADA else toma.avisoHijo,
            )
            val acciones = listOf(Accion.Retirar(toma.clave)) +
                if (avisarHijo) listOf(Accion.AvisarHijo(toma.clave, AvisoHijo.TOMADA)) else emptyList()
            Resultado.Hecho(tomada, acciones)
        }
    }

    /** Corta el sonido de esa toma. No cuenta como tomada: se puede marcar después y el hijo se entera igual. */
    fun silenciar(toma: Toma, ahora: Instant): Resultado = when (toma.estado) {
        Estado.TOMADA -> Resultado.YaTomada(toma.tomadaEn!!)
        Estado.NO_TOMADA -> Resultado.NoPermitido("Esa toma ya se cerró")
        Estado.SILENCIADA -> Resultado.Hecho(toma, emptyList())
        Estado.PENDIENTE ->
            if (ahora < toma.programada) {
                Resultado.NoPermitido("Todavía no es su hora")
            } else {
                Resultado.Hecho(
                    toma.copy(estado = Estado.SILENCIADA, silenciadaEn = ahora),
                    listOf(Accion.Retirar(toma.clave)),
                )
            }
    }

    fun posponer(toma: Toma, ahora: Instant, ajustes: Ajustes): Resultado = when (toma.estado) {
        Estado.TOMADA -> Resultado.YaTomada(toma.tomadaEn!!)
        Estado.NO_TOMADA, Estado.SILENCIADA -> Resultado.NoPermitido("Ya no está sonando")
        Estado.PENDIENTE -> when {
            ahora < toma.programada -> Resultado.NoPermitido("Todavía no es su hora")
            toma.posposiciones >= ajustes.maxPosposiciones -> Resultado.NoPermitido("Ya no se puede posponer más")
            else -> Resultado.Hecho(
                toma.copy(pospuestaHasta = ahora.plus(ajustes.posponer), posposiciones = toma.posposiciones + 1),
                listOf(Accion.Retirar(toma.clave)),
            )
        }
    }

    /**
     * «Ya me la he tomado» antes de hora: crea la siguiente toma de [med] ya marcada.
     * Solo si no hay otra abierta ni otra adelantada, para que no se apunten dos seguidas.
     */
    fun adelantar(med: Medicamento, ultima: Toma?, ahora: Instant, zona: ZoneId): Resultado {
        if (ultima != null && ultima.programada > ahora) {
            return if (ultima.estado == Estado.TOMADA) {
                Resultado.YaTomada(ultima.tomadaEn!!)
            } else {
                Resultado.NoPermitido("Esa toma todavía no ha llegado")
            }
        }
        if (ultima != null && ultima.abierta && ahora < Calendario.cierre(med, ultima, zona)) {
            return Resultado.NoPermitido("Primero marca la de las ${Textos.hora(ultima.programada, zona)}")
        }
        if (!puedeAdelantar(med, ultima, ahora, zona)) {
            return Resultado.NoPermitido("Todavía es pronto para la siguiente")
        }
        val programada = Calendario.siguiente(med, ultima, zona)
        return Resultado.Hecho(Toma(med.id, programada, Estado.TOMADA, tomadaEn = ahora), emptyList())
    }

    /**
     * Si la siguiente toma se puede marcar ya: si es de hoy, o si ya pasó la mitad del camino
     * desde la anterior. Así, recién tomada la de la noche, no se ofrece marcar la de mañana.
     */
    fun puedeAdelantar(med: Medicamento, ultima: Toma?, ahora: Instant, zona: ZoneId): Boolean {
        if (ultima == null) return true
        val siguiente = Calendario.siguiente(med, ultima, zona)
        return ahora >= Calendario.cierre(med, ultima, zona) ||
            siguiente.atZone(zona).toLocalDate() == ahora.atZone(zona).toLocalDate()
    }

    /** Si hay que preguntar «¿Seguro?» antes de marcarla: está antes del margen. */
    fun pideConfirmacion(med: Medicamento, programada: Instant, ahora: Instant): Boolean =
        ahora < programada.minus(med.margen)
}
