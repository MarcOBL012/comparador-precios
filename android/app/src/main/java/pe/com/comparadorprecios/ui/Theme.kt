package pe.com.comparadorprecios.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Color con significado fijo en toda la app: verde = ahorro/mejor precio, naranja = acción u oferta,
// rojo = subió de precio. Sin color dinámico del sistema: si el fondo de pantalla es rojo, el
// "mejor precio" no puede verse rojo.
private val BrandLight = lightColorScheme(
    primary = Color(0xFF0B7A4B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F2D2),
    onPrimaryContainer = Color(0xFF002112),
    secondary = Color(0xFF4E6356),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE9C7),
    onSecondaryContainer = Color(0xFF2B1A00),
    tertiary = Color(0xFFD9480F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCB),
    onTertiaryContainer = Color(0xFF3A0B00),
    background = Color(0xFFF5FAF6),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF5FAF6),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDCE5DD),
    onSurfaceVariant = Color(0xFF404943),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFF7EDBA5),
    onPrimary = Color(0xFF003920),
    primaryContainer = Color(0xFF005234),
    onPrimaryContainer = Color(0xFF9AF8C0),
    secondary = Color(0xFFB4CCBC),
    onSecondary = Color(0xFF203529),
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = Color(0xFFFFE9C7),
    tertiary = Color(0xFFFFB691),
    onTertiary = Color(0xFF5A1B00),
    tertiaryContainer = Color(0xFF7F2B00),
    onTertiaryContainer = Color(0xFFFFDBCB),
    background = Color(0xFF0F1511),
    onBackground = Color(0xFFDEE4DE),
    surface = Color(0xFF0F1511),
    onSurface = Color(0xFFDEE4DE),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C1),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val ExpressiveShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun ComparadorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) BrandDark else BrandLight,
        shapes = ExpressiveShapes,
        content = content,
    )
}
