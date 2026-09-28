package es.abpdev.pastillero

import android.app.Application
import android.content.Context
import es.abpdev.pastillero.actualizacion.ClienteGithub
import es.abpdev.pastillero.datos.BaseDeDatos
import es.abpdev.pastillero.datos.Preferencias
import es.abpdev.pastillero.datos.Repositorio
import es.abpdev.pastillero.sistema.Autoactualizacion
import es.abpdev.pastillero.sistema.ColaAvisosHijo
import es.abpdev.pastillero.sistema.Despertador
import es.abpdev.pastillero.sistema.Notificador
import es.abpdev.pastillero.sistema.Permisos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import java.time.Instant
import java.time.ZoneId

class PastilleroApp : Application() {
    /** Se puede sustituir en los tests por uno con BD en memoria y reloj fijo. */
    lateinit var contenedor: Contenedor

    override fun onCreate() {
        super.onCreate()
        contenedor = Contenedor(this)
        contenedor.notificador.crearCanales()
    }
}

val Context.contenedor: Contenedor get() = (applicationContext as PastilleroApp).contenedor

/** Las piezas de la app, montadas a mano: son pocas y así los tests cambian la BD y el reloj. */
class Contenedor(
    context: Context,
    bd: BaseDeDatos = BaseDeDatos.abrir(context),
    val reloj: Reloj = Reloj { Instant.now() },
    val zona: () -> ZoneId = { ZoneId.systemDefault() },
    val github: ClienteGithub = ClienteGithub(Autoactualizacion.REPO, agente = "Pastillero"),
) {
    val ambito = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val preferencias = Preferencias(context)
    val repositorio = Repositorio(bd, preferencias)
    val notificador = Notificador(context, zona)
    val permisos = Permisos(context)
    val autoactualizacion = Autoactualizacion(context)
    val revisor = Revisor(repositorio, notificador, Despertador(context), ColaAvisosHijo(context), reloj, zona)

    /** Espera a que terminen los trabajos que lanzaron los receptores. */
    suspend fun esperarTrabajos() = ambito.coroutineContext.job.children.toList().joinAll()
}
