package es.abpdev.pastillero.datos

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "medicamentos")
data class FilaMedicamento(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val indicacion: String,
    /** «09:00,21:00» si es a horas fijas; null si es cada X horas. */
    val horas: String?,
    val cadaMinutos: Long?,
    val primeraMs: Long?,
    val margenMinutos: Long,
    val desdeMs: Long,
    val activo: Boolean,
)

/** La clave es (medicamento, hora programada): guardar dos veces la misma toma la actualiza, no la duplica. */
@Entity(
    tableName = "tomas",
    primaryKeys = ["medicamentoId", "programadaMs"],
    indices = [Index("programadaMs")],
)
data class FilaToma(
    val medicamentoId: Long,
    val programadaMs: Long,
    val estado: String,
    val tomadaEnMs: Long?,
    val silenciadaEnMs: Long?,
    val pospuestaHastaMs: Long?,
    val posposiciones: Int,
    val ultimoAvisoMs: Long?,
    val avisoHijo: String,
)

@Dao
interface MedicamentosDao {
    @Query("SELECT * FROM medicamentos ORDER BY id")
    suspend fun todos(): List<FilaMedicamento>

    @Query("SELECT * FROM medicamentos ORDER BY id")
    fun observar(): Flow<List<FilaMedicamento>>

    @Insert
    suspend fun insertar(medicamento: FilaMedicamento): Long

    @Update
    suspend fun actualizar(medicamento: FilaMedicamento)
}

@Dao
interface TomasDao {
    /** Las posteriores a [desdeMs] y, de cada medicamento, la última aunque sea anterior. */
    @Query(
        """SELECT * FROM tomas t WHERE t.programadaMs >= :desdeMs
           OR t.programadaMs = (SELECT MAX(u.programadaMs) FROM tomas u WHERE u.medicamentoId = t.medicamentoId)
           ORDER BY t.programadaMs""",
    )
    suspend fun recientes(desdeMs: Long): List<FilaToma>

    @Query(
        """SELECT * FROM tomas t WHERE t.programadaMs >= :desdeMs
           OR t.programadaMs = (SELECT MAX(u.programadaMs) FROM tomas u WHERE u.medicamentoId = t.medicamentoId)
           ORDER BY t.programadaMs""",
    )
    fun observarRecientes(desdeMs: Long): Flow<List<FilaToma>>

    @Query("SELECT * FROM tomas WHERE medicamentoId = :medicamentoId AND programadaMs = :programadaMs")
    suspend fun una(medicamentoId: Long, programadaMs: Long): FilaToma?

    @Query("SELECT * FROM tomas WHERE medicamentoId = :medicamentoId ORDER BY programadaMs DESC LIMIT 1")
    suspend fun ultima(medicamentoId: Long): FilaToma?

    @Upsert
    suspend fun guardar(tomas: List<FilaToma>)
}

@Database(entities = [FilaMedicamento::class, FilaToma::class], version = 1)
abstract class BaseDeDatos : RoomDatabase() {
    abstract fun medicamentos(): MedicamentosDao

    abstract fun tomas(): TomasDao

    companion object {
        fun abrir(context: Context): BaseDeDatos =
            Room.databaseBuilder(context, BaseDeDatos::class.java, "pastillero.db").build()

        fun enMemoria(context: Context): BaseDeDatos =
            Room.inMemoryDatabaseBuilder(context, BaseDeDatos::class.java).build()
    }
}
