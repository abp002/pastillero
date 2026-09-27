package es.abpdev.pastillero.dominio

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

val ZONA: ZoneId = ZoneId.of("Europe/Madrid")

/** Lunes, horario de verano (UTC+2). */
val HOY: LocalDate = LocalDate.of(2026, 9, 28)

val MEDIA_HORA: Duration = Duration.ofMinutes(30)

fun t(h: Int, m: Int = 0, dia: LocalDate = HOY, s: Int = 0): Instant =
    ZonedDateTime.of(dia, LocalTime.of(h, m, s), ZONA).toInstant()

fun manana(h: Int, m: Int = 0): Instant = t(h, m, HOY.plusDays(1))

fun ayer(h: Int, m: Int = 0): Instant = t(h, m, HOY.minusDays(1))

fun hhmm(instante: Instant): String = instante.atZone(ZONA).format(DateTimeFormatter.ofPattern("HH:mm"))

fun metformina(vararg horas: String = arrayOf("09:00", "21:00"), desde: Instant = t(8)) = Medicamento(
    id = 1,
    nombre = "Metformina",
    indicacion = "1 pastilla con la comida",
    pauta = Pauta.HorasFijas(horas.map(LocalTime::parse)),
    margen = MEDIA_HORA,
    desde = desde,
)

fun tension(desde: Instant = t(8)) = Medicamento(
    id = 2,
    nombre = "Tensión",
    indicacion = "1 pastilla",
    pauta = Pauta.HorasFijas(listOf(LocalTime.of(21, 0))),
    margen = MEDIA_HORA,
    desde = desde,
)

fun cada8h(primera: Instant = t(9), desde: Instant = t(8)) = Medicamento(
    id = 3,
    nombre = "Antibiótico",
    indicacion = "1 cápsula",
    pauta = Pauta.CadaIntervalo(Duration.ofHours(8), primera),
    margen = MEDIA_HORA,
    desde = desde,
)

/**
 * La app en pequeño: guarda las tomas como la BD, aplica los planes y solo se despierta cuando
 * saltaría la alarma programada. Si un test pasa aquí pero el planificador no programa bien la
 * siguiente revisión, el simulador no se despierta y el test lo nota.
 */
class Simulador(
    var medicamentos: List<Medicamento>,
    var ajustes: Ajustes = Ajustes(),
    tomas: Map<ClaveToma, Toma> = emptyMap(),
    val zona: ZoneId = ZONA,
) {
    val tomas = LinkedHashMap(tomas)
    val registro = mutableListOf<Pair<Instant, Accion>>()
    var ahora: Instant = Instant.EPOCH
        private set
    var proxima: Instant? = null
        private set

    fun revisar(en: Instant): Plan {
        ahora = en
        val plan = Planificador.planificar(en, medicamentos, tomas.values.toList(), ajustes, zona)
        plan.proximaRevision?.let { check(it > en) { "Próxima revisión $it no posterior a $en" } }
        plan.tomas.forEach { tomas[it.clave] = it }
        plan.acciones.forEach { registro += en to it }
        proxima = plan.proximaRevision
        return plan
    }

    /** Como en el móvil: solo se despierta cuando salta la alarma programada. */
    fun avanzarHasta(hasta: Instant) {
        while (true) {
            val siguiente = proxima ?: break
            if (siguiente > hasta) break
            revisar(siguiente)
        }
        ahora = maxOf(ahora, hasta)
    }

    fun toma(medicamentoId: Long, programada: Instant): Toma = tomas.getValue(ClaveToma(medicamentoId, programada))

    fun abiertas(): List<Toma> = tomas.values.filter { it.abierta }

    fun ultima(medicamentoId: Long): Toma? =
        tomas.values.filter { it.medicamentoId == medicamentoId }.maxByOrNull { it.programada }

    fun pulsarTomada(clave: ClaveToma, en: Instant) = pulsar(en) {
        Operaciones.marcarTomada(tomas.getValue(clave), en, ajustes)
    }

    fun pulsarSilenciar(clave: ClaveToma, en: Instant) = pulsar(en) {
        Operaciones.silenciar(tomas.getValue(clave), en)
    }

    fun pulsarPosponer(clave: ClaveToma, en: Instant) = pulsar(en) {
        Operaciones.posponer(tomas.getValue(clave), en, ajustes)
    }

    fun pulsarAdelantar(medicamentoId: Long, en: Instant) = pulsar(en) {
        Operaciones.adelantar(medicamentos.single { it.id == medicamentoId }, ultima(medicamentoId), en, zona)
    }

    /** Lo mismo que hace la app tras una pulsación: aplicar y volver a revisar. */
    private fun pulsar(en: Instant, operacion: () -> Resultado): Resultado {
        avanzarHasta(en)
        val resultado = operacion()
        if (resultado is Resultado.Hecho) {
            tomas[resultado.toma.clave] = resultado.toma
            resultado.acciones.forEach { registro += en to it }
        }
        revisar(en)
        return resultado
    }

    fun sonidos(desde: Instant = Instant.MIN): List<String> = registro
        .filter { (en, accion) -> accion is Accion.Sonar && en >= desde }
        .map { (en, accion) -> "${hhmm(en)} ${(accion as Accion.Sonar).nivel}" }

    fun avisosHijo(): List<String> = registro
        .filter { it.second is Accion.AvisarHijo }
        .map { (en, accion) -> "${hhmm(en)} ${(accion as Accion.AvisarHijo).tipo}" }
}
