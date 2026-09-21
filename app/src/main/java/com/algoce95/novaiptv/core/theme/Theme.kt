package com.algoce95.novaiptv.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** Paleta única de la app (paridad con AppColors de Flutter). */
object AppColors {
    // Base.
    val ink = Color(0xFF0B1117)
    val panel = Color(0xFF15212A)
    val accent = Color(0xFFFF6B4A)
    val mint = Color(0xFF5DE0C2)
    val amber = Color(0xFFFFC857)
    val pink = Color(0xFFE91E63)

    // Superficies y focos.
    val focusFill = Color(0xFF1D3039)
    val bottomNav = Color(0xFF1F1F1F)
    val playerInk = Color(0xFF121212)
    val epgLive = Color(0xFF1D403D)
    val searchTile = Color(0xFF1E1E1E)
    val searchTileFocus = Color(0xFF2A0A1A)
    val episodeFocus = Color(0xFF3B211C)
    val thumbPlaceholder = Color(0xFF2C2C2C)
    val posterFallback = Color(0xFF20323A)
    val movieButtonDark = Color(0xFF880E4F)

    // Degradados.
    val cardGradStart = Color(0xFF29434A)
    val cardGradEnd = Color(0xFF17262E)
    val contentGradEnd = Color(0xFF10232B)
    val dashboardGradEnd = Color(0xFF12232A)
    val seriesHeadStart = Color(0xFF1C333B)

    // Acentos secundarios.
    val chipFocus = Color(0xFFB94732)
    val salmon = Color(0xFFFF8B70)
    val mutedLabel = Color(0xFF8CAAA9)
    val expired = Color(0xFFFF5252)
    val dotGreen = Color(0xFF69F0AE)
    val dotBlue = Color(0xFF448AFF)
    val badgeScrim = Color(0xE60B1117)
    val badgeDark = Color(0xDD0B1117)

    // Velo del degradado de controles del reproductor.
    val scrimStrong = Color(0xCC000000)
    val scrimFaint = Color(0x22000000)
    val scrimBottom = Color(0xE6000000)

    // Grises de texto base (el ajuste de contraste los aclara un escalón).
    val dimText = Color(0xB3FFFFFF)
    val subtleText = Color(0x8AFFFFFF)
    val faintText = Color(0x61FFFFFF)

    // Superficies neutras adicionalés.
    val tileHeader = Color(0xFF171923)
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
