package es.abpdev.pastillero.dominio

import java.time.ZoneId

/** Lo que se puede pulsar sobre una toma que suena, desde la notificación o desde la app. */
enum class Gesto { TOMADA, POSPONER, SILENCIAR }

/** Qué ha pasado al pulsar, contado para que no quede ninguna duda. */
data class Confirmacion(val tipo: Tipo, val titulo: String, val detalle: String) {
    enum class Tipo { BIEN, LUEGO, APAGADA, AVISO }
}

object Confirmaciones {
    fun de(gesto: Gesto, resultado: Resultado, nombre: String, zona: ZoneId): Confirmacion = when (resultado) {
        is Resultado.YaTomada ->
            Confirmacion(Confirmacion.Tipo.BIEN, "✓ Ya estaba apuntada", "$nombre, a las ${Textos.hora(resultado.tomadaEn, zona)}")
        is Resultado.NoPermitido -> Confirmacion(Confirmacion.Tipo.AVISO, "No se ha podido", resultado.motivo)
        is Resultado.Hecho -> {
            val toma = resultado.toma
            when (gesto) {
                Gesto.TOMADA ->
                    Confirmacion(Confirmacion.Tipo.BIEN, "✓ Apuntada", "$nombre, a las ${Textos.hora(toma.tomadaEn!!, zona)}")
                Gesto.POSPONER ->
                    Confirmacion(Confirmacion.Tipo.LUEGO, "Te lo recuerdo a las ${Textos.hora(toma.pospuestaHasta!!, zona)}", nombre)
                Gesto.SILENCIAR ->
                    Confirmacion(
                        Confirmacion.Tipo.APAGADA,
                        "Alarma apagada",
                        "$nombre sigue sin marcar. Cuando te la tomes, márcala aquí.",
                    )
            }
        }
    }
}
