package com.algoce95.novaiptv.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Paleta única de la app: azure/celeste sobre navy casi negro. */
object AppColors {
    // Base.
    val ink = Color(0xFF060C15)
    val panel = Color(0xFF0F1B2A)
    val accent = Color(0xFF2E8FFF)
    val mint = Color(0xFF45E0C0)
    val amber = Color(0xFFFFC857)
    val sky = Color(0xFF4FC3F7)

    // Superficies y focos.
    val focusFill = Color(0xFF16293D)
    val bottomNav = Color(0xFF0C1522)
    val playerInk = Color(0xFF080E16)
    val epgLive = Color(0xFF113744)
    val searchTile = Color(0xFF12202F)
    val searchTileFocus = Color(0xFF123049)
    val episodeFocus = Color(0xFF14304A)
    val thumbPlaceholder = Color(0xFF1A2836)
    val posterFallback = Color(0xFF17293A)
    val movieButtonDark = Color(0xFF0D3A66)

    // Degradados.
    val cardGradStart = Color(0xFF1C3E5E)
    val cardGradEnd = Color(0xFF0D1B2A)
    val contentGradEnd = Color(0xFF0B1A2B)
    val dashboardGradEnd = Color(0xFF0C1A2A)
    val seriesHeadStart = Color(0xFF142C44)
    val heroGradMid = Color(0xB3060C15)

    // Acentos secundarios.
    val accentBright = Color(0xFF63AEFF)
    val mutedLabel = Color(0xFF8FA9C4)
    val expired = Color(0xFFFF5252)
    val dotGreen = Color(0xFF69F0AE)
    val dotBlue = Color(0xFF448AFF)
    val badgeScrim = Color(0xE6060C15)
    val badgeDark = Color(0xDD060C15)

    // Velo del degradado de controles del reproductor.
    val scrimStrong = Color(0xCC000000)
    val scrimFaint = Color(0x22000000)
    val scrimBottom = Color(0xE6000000)

    // Grises de texto base (el ajuste de contraste los aclara un escalón).
    val dimText = Color(0xB3FFFFFF)
    val subtleText = Color(0x8AFFFFFF)
    val faintText = Color(0x61FFFFFF)

    // Superficies neutras adicionales.
    val tileHeader = Color(0xFF101C2B)
    val posterSurface = Color(0xFF000000)
}

/** Ajuste "alto contraste", propagado por CompositionLocal desde HomeScreen. */
val LocalHighContrast = compositionLocalOf { false }

/** Texto principal: blanco puro con alto contraste. */
@Composable
fun bodyTextColor(): Color = if (LocalHighContrast.current) Color.White else AppColors.dimText

/** Texto secundario: sube un escalón con alto contraste. */
@Composable
fun subtleTextColor(): Color =
    if (LocalHighContrast.current) AppColors.dimText else AppColors.subtleText

/** Texto tenue: sube un escalón con alto contraste. */
@Composable
fun faintTextColor(): Color =
    if (LocalHighContrast.current) AppColors.subtleText else AppColors.faintText

/**
 * Escala tipográfica única. Los tamaños pensados para visión a 3 m (TV) quedan
 * aquí; las pantallas no deben declarar `fontSize` sueltas.
 */
object NovaType {
    val display = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.W700)
    val title = TextStyle(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.W700)
    val sectionTitle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.W700)
    val subtitle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.W600)
    val body = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val cardTitle = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.W600)
    val meta = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.W500)
    val caption = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.W500)
    val badge = TextStyle(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.W700)
}

/** Radios compartidos: 14.dp es el que usa el patrón de foco de TV. */
object NovaShapes {
    val card = RoundedCornerShape(14.dp)
    val tile = RoundedCornerShape(18.dp)
    val poster = RoundedCornerShape(10.dp)
    val chip = RoundedCornerShape(10.dp)
    val pill = RoundedCornerShape(999.dp)
}

private val NovaColorScheme = darkColorScheme(
    primary = AppColors.accent,
    secondary = AppColors.mint,
    tertiary = AppColors.amber,
    background = AppColors.ink,
    surface = AppColors.panel,
)

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NovaColorScheme,
        content = content,
    )
}
