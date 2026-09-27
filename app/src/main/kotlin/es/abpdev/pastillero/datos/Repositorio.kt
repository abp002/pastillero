package es.abpdev.pastillero.datos

import es.abpdev.pastillero.dominio.AvisoHijo
import es.abpdev.pastillero.dominio.ClaveToma
import es.abpdev.pastillero.dominio.Estado
import es.abpdev.pastillero.dominio.Medicamento
import es.abpdev.pastillero.dominio.Pauta
import es.abpdev.pastillero.dominio.Toma
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/** Traduce entre las filas de Room y el dominio. */
class Repositorio(private val bd: BaseDeDatos, val preferencias: Preferencias) {

    suspend fun medicamentos(): List<Medicamento> = bd.medicamentos().todos().map { it.aDominio() }

    fun observarMedicamentos(): Flow<List<Medicamento>> =
        bd.medicamentos().observar().map { filas -> filas.map { it.aDominio() } }

    /** Guarda y devuelve el id (nuevo si [medicamento] tenía id 0). */
    suspend fun guardarMedicamento(medicamento: Medicamento): Long =
        if (medicamento.id == 0L) {
            bd.medicamentos().insertar(medicamento.aFila())
        } else {
            bd.medicamentos().actualizar(medicamento.aFila())
            medicamento.id
        }

    suspend fun tomasRecientes(desde: Instant): List<Toma> =
        bd.tomas().recientes(desde.toEpochMilli()).map { it.aDominio() }

    fun observarTomas(desde: Instant): Flow<List<Toma>> =
        bd.tomas().observarRecientes(desde.toEpochMilli()).map { filas -> filas.map { it.aDominio() } }

    suspend fun toma(clave: ClaveToma): Toma? =
        bd.tomas().una(clave.medicamentoId, clave.programada.toEpochMilli())?.aDominio()

    suspend fun ultima(medicamentoId: Long): Toma? = bd.tomas().ultima(medicamentoId)?.aDominio()

    suspend fun guardarTomas(tomas: List<Toma>) {
        if (tomas.isNotEmpty()) bd.tomas().guardar(tomas.map { it.aFila() })
    }

    fun ajustes(): AjustesApp = preferencias.actual()
}

private fun FilaMedicamento.aDominio() = Medicamento(
    id = id,
    nombre = nombre,
    indicacion = indicacion,
    pauta = if (horas != null) {
        Pauta.HorasFijas(horas.split(',').map(LocalTime::parse))
    } else {
        Pauta.CadaIntervalo(Duration.ofMinutes(cadaMinutos!!), Instant.ofEpochMilli(primeraMs!!))
    },
    margen = Duration.ofMinutes(margenMinutos),
    desde = Instant.ofEpochMilli(desdeMs),
    activo = activo,
)

private fun Medicamento.aFila(): FilaMedicamento {
    val fijas = pauta as? Pauta.HorasFijas
    val intervalo = pauta as? Pauta.CadaIntervalo
    return FilaMedicamento(
        id = id,
        nombre = nombre,
        indicacion = indicacion,
        horas = fijas?.horas?.sorted()?.joinToString(",") { it.toString() },
        cadaMinutos = intervalo?.cada?.toMinutes(),
        primeraMs = intervalo?.primera?.toEpochMilli(),
        margenMinutos = margen.toMinutes(),
        desdeMs = desde.toEpochMilli(),
        activo = activo,
    )
}

private fun FilaToma.aDominio() = Toma(
    medicamentoId = medicamentoId,
    programada = Instant.ofEpochMilli(programadaMs),
    estado = Estado.valueOf(estado),
    tomadaEn = tomadaEnMs?.let(Instant::ofEpochMilli),
    silenciadaEn = silenciadaEnMs?.let(Instant::ofEpochMilli),
    pospuestaHasta = pospuestaHastaMs?.let(Instant::ofEpochMilli),
    posposiciones = posposiciones,
    ultimoAviso = ultimoAvisoMs?.let(Instant::ofEpochMilli),
    avisoHijo = AvisoHijo.valueOf(avisoHijo),
)

private fun Toma.aFila() = FilaToma(
    medicamentoId = medicamentoId,
    programadaMs = programada.toEpochMilli(),
    estado = estado.name,
    tomadaEnMs = tomadaEn?.toEpochMilli(),
    silenciadaEnMs = silenciadaEn?.toEpochMilli(),
    pospuestaHastaMs = pospuestaHasta?.toEpochMilli(),
    posposiciones = posposiciones,
    ultimoAvisoMs = ultimoAviso?.toEpochMilli(),
    avisoHijo = avisoHijo.name,
)
