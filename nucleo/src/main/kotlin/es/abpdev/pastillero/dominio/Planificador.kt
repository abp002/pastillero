package es.abpdev.pastillero.dominio

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Decide qué suena, qué se cierra y a quién se avisa, a partir del estado y la hora.
 *
 * Es una función pura: la app la llama al saltar la alarma, al arrancar el móvil, al cambiar
 * la hora o tras cada pulsación, guarda [Plan.tomas], ejecuta [Plan.acciones] y programa una
 * sola alarma en [Plan.proximaRevision]. Llamarla dos veces seguidas no repite nada.
 */
object Planificador {
    /** Al volver de mucho tiempo apagado, las tomas más antiguas que esto no se apuntan. */
    private val HISTORIA: Duration = Duration.ofDays(7)

    fun planificar(
        ahora: Instant,
        medicamentos: List<Medicamento>,
        tomas: List<Toma>,
        ajustes: Ajustes,
        zona: ZoneId,
    ): Plan {
        val cambiadas = LinkedHashMap<ClaveToma, Toma>()
        val acciones = mutableListOf<Accion>()
        var proxima: Instant? = null
        fun despertarEn(instante: Instant) {
            if (instante > ahora && (proxima == null || instante < proxima)) proxima = instante
        }

        val porMedicamento = tomas.groupBy { it.medicamentoId }
        for (med in medicamentos) {
            val propias = porMedicamento[med.id].orEmpty()
            if (!med.activo) {
                propias.filter { it.abierta }.forEach {
                    cambiadas[it.clave] = it.copy(estado = Estado.NO_TOMADA)
                    acciones += Accion.Retirar(it.clave)
                }
                continue
            }

            // Las tomas cuya hora ya ha llegado y todavía no existen.
            val orden = propias.sortedBy { it.programada }.toMutableList()
            val nuevas = HashSet<ClaveToma>()
            var ultima = orden.lastOrNull()
            while (true) {
                val programada = Calendario.siguiente(med, ultima, zona)
                if (programada > ahora) break
                val nueva = Toma(med.id, programada)
                if (programada >= ahora.minus(HISTORIA)) {
                    orden += nueva
                    nuevas += nueva.clave
                    cambiadas[nueva.clave] = nueva
                }
                ultima = nueva
            }

            for (original in orden) {
                if (!original.abierta) continue
                var toma = original

                val cierre = Calendario.cierre(med, toma, zona)
                if (ahora >= cierre) {
                    cambiadas[toma.clave] = toma.copy(estado = Estado.NO_TOMADA)
                    // Una toma que nace ya cerrada (el móvil estuvo apagado) nunca tuvo aviso que quitar.
                    if (toma.clave !in nuevas) acciones += Accion.Retirar(toma.clave)
                    continue
                }
                despertarEn(cierre)

                if (toma.estado == Estado.PENDIENTE) {
                    val base = maxOf(toma.programada, toma.pospuestaHasta ?: toma.programada)
                    if (ahora < base) {
                        despertarEn(base)
                    } else {
                        val paso = ajustes.intervaloAvisos.toMillis()
                        val punto = base.plusMillis(Duration.between(base, ahora).toMillis() / paso * paso)
                        val yaSono = toma.ultimoAviso?.let { it >= punto } ?: false
                        if (!yaSono) {
                            val nivel = if (Duration.between(toma.programada, punto) >= ajustes.alarmaTras) Nivel.ALARMA else Nivel.AVISO
                            acciones += Accion.Sonar(toma.clave, nivel, toma.posposiciones < ajustes.maxPosposiciones)
                            toma = toma.copy(ultimoAviso = ahora)
                        }
                        despertarEn(punto.plusMillis(paso))
                    }
                }

                if (ajustes.avisoHijoActivo && toma.avisoHijo == AvisoHijo.NINGUNO) {
                    val cuando = toma.programada.plus(ajustes.avisoHijoTras)
                    if (ahora >= cuando) {
                        acciones += Accion.AvisarHijo(toma.clave, AvisoHijo.SIN_CONFIRMAR)
                        toma = toma.copy(avisoHijo = AvisoHijo.SIN_CONFIRMAR)
                    } else {
                        despertarEn(cuando)
                    }
                }

                if (toma != original) cambiadas[toma.clave] = toma
            }

            despertarEn(Calendario.siguiente(med, ultima, zona))
        }

        return Plan(cambiadas.values.toList(), acciones, proxima)
    }
}
