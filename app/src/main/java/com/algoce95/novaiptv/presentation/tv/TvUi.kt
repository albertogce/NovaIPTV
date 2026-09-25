package com.algoce95.novaiptv.presentation.tv

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algoce95.novaiptv.core.theme.AppColors
import com.algoce95.novaiptv.core.theme.faintTextColor
import com.algoce95.novaiptv.core.theme.subtleTextColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Radius de todos los ítems escalables; coincide con las tarjetas de rejilla. */
val TvFocusShape = RoundedCornerShape(14.dp)

/**
 * Escala animada al enfocar (estándar leanback): es el indicador principal de
 * foco en TV; los bordes solo acompañan. Debe aplicarse DESPUÉS de
 * clip/background/border para que escale el dibujo y no afecte al layout.
 */
@Composable
fun Modifier.tvFocusScale(focused: Boolean, scale: Float = 1.05f): Modifier {
    val progress by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(140),
        label = "tvFocusScale",
    )
    return this.graphicsLayer {
        val s = 1f + (scale - 1f) * progress
        scaleX = s
        scaleY = s
    }
}

/** Borde de foco unificado: 2dp de [color] sobre la misma [TvFocusShape]. */
fun Modifier.tvFocusBorder(focused: Boolean, color: Color = AppColors.mint): Modifier =
    border(if (focused) 2.dp else 1.dp, if (focused) color else Color.Transparent, TvFocusShape)

/**
 * Patrón de foco completo de la app: foco D-pad + borde de acento + escala
 * animada + sombra. Clave OK: si [enabled] es nulo se dispara [onSelect] al
 * soltar; si no, queda en manos del llamador (por ejemplo donde hay pulación
 * larga) para no activar dos veces.
 */
@Composable
fun Modifier.tvFocusItem(
    focused: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean? = null,
    scale: Float = 1.05f,
    borderColor: Color = AppColors.mint,
    modifier: Modifier = Modifier,
): Modifier {
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    return this
        .clip(TvFocusShape)
        .then(if (focused) Modifier.shadow(14.dp, TvFocusShape) else Modifier)
        .then(modifier)
        .border(1.dp, Color.White.copy(alpha = 0.08f), TvFocusShape)
        .tvFocusBorder(focused, borderColor)
        .focusable(interactionSource = interaction)
        .onPreviewKeyEvent { event ->
            if (enabled != null) return@onPreviewKeyEvent false
            if (focused && event.isKeyUp() && TvKeys.isSelectKey(event.key)) {
                scope.launch { onSelect() }
                true
            } else {
                false
            }
        }
        .tvFocusScale(focused, scale)
}

/**
 * `requestFocus()` lanza [IllegalStateException] si el nodo aún no está compuesto
 * (listas perezosas, ramas condicionales). En TV eso es frecuente; se ignora el intento.
 */
fun FocusRequester.tryRequestFocus() = runCatching { requestFocus() }

/**
 * Reintenta el foco cuadro a cuadro: en listas perezosas el nodo puede no estar
 * compuesto todavía en el primer intento (cambiar de categoría o de temporada
 * deja la fila fuera del viewport). Devuelve false si nunca llegó.
 */
suspend fun FocusRequester.requestFocusReady(maxAttempts: Int = 8): Boolean {
    repeat(maxAttempts) {
        withFrameNanos { }
        if (tryRequestFocus().isSuccess) return true
    }
    return false
}

/**
 * Utilidades de mando a distancia (paridad con los `onKeyEvent` de Flutter).
 * Los botones de color llegan con keyCode Android 183-186.
 */
object TvKeys {
    const val RED = 183
    const val GREEN = 184
    const val YELLOW = 185
    const val BLUE = 186

    fun isSelectKey(key: Key): Boolean =
        key == Key.Enter ||
            key == Key.NumPadEnter ||
            key == Key.Spacebar ||
            key == Key.DirectionCenter

    fun colorName(keyCode: Int): String? = when (keyCode) {
        RED -> "ROJO"
        GREEN -> "VERDE"
        YELLOW -> "AMARILLO"
        BLUE -> "AZUL"
        else -> null
    }
}

fun KeyEvent.isKeyDown(): Boolean = type == KeyEventType.KeyDown

fun KeyEvent.isKeyUp(): Boolean = type == KeyEventType.KeyUp

/** Repetición mantenida (equivale a KeyRepeatEvent). */
fun KeyEvent.isRepeat(): Boolean = nativeKeyEvent.repeatCount > 0

/**
 * Estado de foco para TV observado vía interactionSource (más fiable que
 * onFocusChanged en este setup). Uso:
 * `val focus = rememberTvFocus()` +
 * `Modifier.focusable(interactionSource = focus.interaction)` y leer `focus.focused`.
 */
@Immutable
data class TvFocus(val interaction: MutableInteractionSource, val focused: Boolean)

@Composable
fun rememberTvFocus(): TvFocus {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    return TvFocus(interaction, focused)
}

/**
 * Texto con marquesina: si desborda, se desplaza en bucle (réplica de `_MarqueeText`).
 */
@Composable
fun MarqueeText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    var overflowPx by remember(text) { mutableStateOf(0) }
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        if (overflowPx <= 0) {
            Text(
                text = text,
                style = style,
                maxLines = 1,
                onTextLayout = { result ->
                    val lineWidth = result.getLineRight(0) - result.getLineLeft(0)
                    if (lineWidth > maxWidthPx) {
                        // Recorrido + hueco de separación.
                        overflowPx = (lineWidth - maxWidthPx).toInt() + 96
                    }
                },
            )
        } else {
            val transition = rememberInfiniteTransition(label = "marquee")
            val offset by transition.animateFloat(
                initialValue = 0f,
                targetValue = -overflowPx.toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 7000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "marqueeOffset",
            )
            Text(
                text = "$text    $text",
                style = style,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer { translationX = offset },
            )
        }
    }
}

/**
 * Tap corto frente a pulsación larga (600 ms) para táctil y D-pad.
 * Réplica de `_GridItem`: ignora la pulsación inicial tras autofocus,
 * dispara [onLongPress] al expirar el temporizador o [onTap] al soltar.
 */
@Composable
fun Modifier.tvPress(
    enabled: Boolean = true,
    autofocus: Boolean = false,
    ignoreInitialSelect: Boolean = false,
    fireOnDown: Boolean = false,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
): Modifier {
    val scope = rememberCoroutineScope()
    var timerJob by remember { mutableStateOf<Job?>(null) }
    var longFired by remember { mutableStateOf(false) }
    var suppressNextTap by remember { mutableStateOf(false) }
    var sawKeyDown by remember { mutableStateOf(false) }
    var ignoreUntil by remember { mutableLongStateOf(0L) }
    LaunchedEffect(autofocus, ignoreInitialSelect) {
        if (autofocus && ignoreInitialSelect) {
            ignoreUntil = android.os.SystemClock.uptimeMillis() + 500
        }
    }
    return this
        .pointerInput(onTap, onLongPress) {
            detectTapGestures(
                onLongPress = {
                    // Mark it as handled as well as invoking the
                    // action, so a following release cannot become a
                    // short press if the item recomposes.
                    longFired = true
                    onLongPress?.invoke()
                },
                onTap = { onTap() },
            )
        }
        .onPreviewKeyEvent { event ->
            if (!enabled || !TvKeys.isSelectKey(event.key)) return@onPreviewKeyEvent false
            if (android.os.SystemClock.uptimeMillis() < ignoreUntil) {
                return@onPreviewKeyEvent true
            }
            when (event.type) {
                KeyEventType.KeyDown -> {
                    sawKeyDown = true
                    if (fireOnDown && onLongPress == null) {
                        onTap()
                        return@onPreviewKeyEvent true
                    }
                    // Repetición mantenida: el temporizador ya corre.
                    if (timerJob?.isActive == true) return@onPreviewKeyEvent true
                    longFired = false
                    if (onLongPress != null) {
                        timerJob = scope.launch {
                            delay(600)
                            longFired = true
                            suppressNextTap = true
                            onLongPress()
                        }
                    }
                    true
                }
                KeyEventType.KeyUp -> {
                    val ownsPress = sawKeyDown
                    sawKeyDown = false
                    timerJob?.cancel()
                    timerJob = null
                    if (fireOnDown && onLongPress == null) return@onPreviewKeyEvent true
                    // Soltar la MISMA tecla que ya actuó en otro ítem: al cambiar
                    // de pantalla el foco cae en un nodo recién compuesto que nunca
                    // vio el pulsar, y sin esto esa suelta disparaba una segunda
                    // acción (abrir "Seguir viendo" reproducía su primer elemento).
                    if (!ownsPress) return@onPreviewKeyEvent true
                    // A long press already performed its action. In
                    // particular, do not open a channel on the release of
                    // the same held Enter key.
                    if (!longFired && !suppressNextTap) onTap()
                    suppressNextTap = false
                    longFired = false
                    true
                }
                else -> true
            }
        }
}

/** Estado vacío compartido: icono grande + título + pista opcional. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.White.copy(alpha = 0.14f),
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(58.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.W700,
            textAlign = TextAlign.Center,
        )
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = hint,
                color = subtleTextColor(),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Botón de acento compartido (mismo lenguaje visual que las tarjetas). */
@Composable
fun NovaButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val focus = rememberTvFocus()
    val focused = focus.focused
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) AppColors.accentBright else AppColors.accent)
            .border(2.dp, Color.White.copy(alpha = if (focused) 0.9f else 0f), RoundedCornerShape(12.dp))
            .focusable(interactionSource = focus.interaction)
            .tvPress(fireOnDown = true, onTap = onClick)
            .padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
        }
        Text(text = label, color = Color.White, fontWeight = FontWeight.W700, fontSize = 14.sp)
    }
}

/**
 * Barrido de brillo para pósters en carga: mucho más limpio que un spinner
 * cuando hay decenas de tarjetas cargando a la vez.
 */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val base = AppColors.posterFallback
    val highlight = AppColors.cardGradStart
    val transition = rememberInfiniteTransition(label = "shimmer")
    val slide by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerSlide",
    )
    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .background(base),
        contentAlignment = Alignment.TopStart,
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        Box(
            modifier = Modifier
                .fillMaxWidth(2f)
                .fillMaxHeight()
                .graphicsLayer { translationX = slide * widthPx }
                .background(
                    Brush.horizontalGradient(
                        listOf(base, highlight, base),
                    ),
                ),
        )
    }
}
