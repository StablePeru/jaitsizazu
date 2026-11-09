package jaitsizazu.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jaitsizazu.ui.resources.Res
import jaitsizazu.ui.resources.space_grotesk_medium
import org.jetbrains.compose.resources.Font

/**
 * The palette of the app: pure black, near-white text and greys, no other
 * colour. Every grey used for text passes 4.5:1 on [Background].
 */
internal object Palette {
    val Background = Color(0xFF000000)
    val Text = Color(0xFFF5F5F5)

    /** Secondary text: hints, paths, notes. */
    val Muted = Color(0xFF8C8C8C)

    /** The tab that is not shown. */
    val Dim = Color(0xFF808080)

    /** Disabled text and the outline of an unselected choice. */
    val Faint = Color(0xFF5C5C5C)

    /** The line under a field. */
    val Line = Color(0xFF3A3A3A)

    /** Dividers between rows, the progress track, a disabled button. */
    val Hairline = Color(0xFF1C1C1C)

    val Error = Color(0xFFFF8A80)
}

/**
 * The dark theme of every platform. Headings and tabs use Space Grotesk,
 * bundled under the SIL Open Font License (files/licenses/SpaceGrotesk-OFL.txt);
 * the rest uses the system font.
 */
@Composable
fun JaitsizazuTheme(content: @Composable () -> Unit) {
    val display = FontFamily(Font(Res.font.space_grotesk_medium, FontWeight.Medium))
    MaterialTheme(
        colors = darkColors(
            primary = Palette.Text,
            primaryVariant = Palette.Text,
            secondary = Palette.Text,
            secondaryVariant = Palette.Text,
            background = Palette.Background,
            surface = Palette.Background,
            error = Palette.Error,
            onPrimary = Palette.Background,
            onSecondary = Palette.Background,
            onBackground = Palette.Text,
            onSurface = Palette.Text,
            onError = Palette.Background,
        ),
        typography = Typography(
            h5 = TextStyle(fontFamily = display, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
            h6 = TextStyle(fontFamily = display, fontWeight = FontWeight.Medium, fontSize = 18.sp),
            body1 = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
            body2 = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
            button = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
            caption = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
            overline = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.7.sp),
        ),
        shapes = Shapes(small = RoundedCornerShape(percent = 50), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp)),
        content = content,
    )
}
