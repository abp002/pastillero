package es.abpdev.pastillero.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colores por estado de la toma: se leen de un vistazo y con buen contraste. */
object Colores {
    val fondo = Color(0xFFFDF8F1)
    val verde = Color(0xFF1F6F5C)
    val verdeClaro = Color(0xFFE2F3EC)
    val ambar = Color(0xFF8A5A00)
    val ambarClaro = Color(0xFFFFEDC7)
    val rojo = Color(0xFFA1281B)
    val rojoClaro = Color(0xFFFDE4E1)
    val tinta = Color(0xFF1D1B18)
    val tintaSuave = Color(0xFF5C5750)
    val tarjeta = Color(0xFFFFFFFF)
}

private val esquema = lightColorScheme(
    primary = Colores.verde,
    onPrimary = Color.White,
    background = Colores.fondo,
    onBackground = Colores.tinta,
    surface = Colores.fondo,
    onSurface = Colores.tinta,
    onSurfaceVariant = Colores.tintaSuave,
    error = Colores.rojo,
)

/** Letra grande: lo va a leer una persona mayor, a veces sin gafas. */
private val letra = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontSize = 24.sp),
        titleMedium = titleMedium.copy(fontSize = 21.sp),
        bodyLarge = bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
        bodyMedium = bodyMedium.copy(fontSize = 18.sp, lineHeight = 25.sp),
        labelLarge = labelLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    )
}

val TextoBotonGrande = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)

@Composable
fun TemaPastillero(contenido: @Composable () -> Unit) =
    MaterialTheme(colorScheme = esquema, typography = letra, content = contenido)
