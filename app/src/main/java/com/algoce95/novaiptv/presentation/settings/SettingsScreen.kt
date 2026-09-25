package com.algoce95.novaiptv.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import com.algoce95.novaiptv.presentation.tv.isKeyDown
import com.algoce95.novaiptv.core.di.AppContainer
import com.algoce95.novaiptv.core.storage.PrefsStore
import com.algoce95.novaiptv.core.theme.AppColors
import com.algoce95.novaiptv.core.theme.LocalHighContrast
import com.algoce95.novaiptv.core.theme.subtleTextColor
import com.algoce95.novaiptv.data.metadata.PosterResolver
import com.algoce95.novaiptv.presentation.home.HomeCatalogViewModel
import com.algoce95.novaiptv.presentation.tv.rememberTvFocus
import com.algoce95.novaiptv.presentation.tv.tryRequestFocus
import com.algoce95.novaiptv.presentation.tv.tvPress
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val COLOR_KEYS = listOf("ROJO", "VERDE", "AMARILLO", "AZUL")
private val COLOR_ACTIONS = linkedMapOf(
    "none" to "Sin asignar",
    "favorites" to "Favoritos",
    "search" to "Buscar",
    "live" to "Canales en vivo",
    "movies" to "Películas",
    "series" to "Series",
    "continueWatching" to "Seguir viendo",
)

private fun dotColor(key: String): Color = when (key) {
    "ROJO" -> AppColors.expired
    "VERDE" -> AppColors.dotGreen
    "AMARILLO" -> AppColors.amber
    else -> AppColors.dotBlue
}

/** Paridad con `SettingsScreen` de Flutter (autoguardado + aviso al salir). */
@Composable
fun SettingsScreen(vm: HomeCatalogViewModel, onDone: () -> Unit) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val prefs = AppContainer.prefs
    val credentials = AppContainer.credentials
    val client = AppContainer.api
    if (client == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Sin sesión activa", color = subtleTextColor())
        }
        return
    }

    var url by rememberSaveable { mutableStateOf(credentials.server.ifEmpty { client.baseUrl }) }
    var username by rememberSaveable { mutableStateOf(credentials.username.ifEmpty { client.username }) }
    var password by rememberSaveable { mutableStateOf(credentials.password.ifEmpty { client.password }) }
    var refreshDays by rememberSaveable { mutableStateOf(0) }
    var scale by rememberSaveable { mutableStateOf(1f) }
    var density by rememberSaveable { mutableStateOf(0) }
    var contrast by rememberSaveable { mutableStateOf(false) }
    var storeVisibleOnly by rememberSaveable { mutableStateOf(false) }
    var colors by rememberSaveable { mutableStateOf(COLOR_KEYS.associateWith { "none" }) }
    var tmdbKey by rememberSaveable { mutableStateOf("") }

    var liveCats by remember { mutableStateOf(state.allLiveCats) }
    var hiddenLive by remember { mutableStateOf(setOf<String>()) }
    var vodCats by remember { mutableStateOf(state.allVodCats) }
    var hiddenVod by remember { mutableStateOf(setOf<String>()) }
    var seriesCats by remember { mutableStateOf(state.allSeriesCats) }
    var hiddenSeries by remember { mutableStateOf(setOf<String>()) }
    var tab by rememberSaveable { mutableStateOf(0) }
    val scaleFocus = remember { FocusRequester() }
    val urlFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        scaleFocus.tryRequestFocus()
        val daysStr = prefs.getString(PrefsStore.Keys.AUTO_REFRESH_DAYS)
        refreshDays = daysStr?.toIntOrNull() ?: run {
            val mins = prefs.getString(PrefsStore.Keys.AUTO_REFRESH_MINUTES)?.toIntOrNull() ?: 0
            (mins / 1440.0).toInt().let { if (it < 0) 0 else it }
        }
        scale = prefs.getString(PrefsStore.Keys.CARD_SCALE)?.toFloatOrNull() ?: 1f
        density = prefs.getString(PrefsStore.Keys.GRID_DENSITY)?.toIntOrNull() ?: 0
        contrast = prefs.getBoolean(PrefsStore.Keys.HIGH_CONTRAST) ?: false
        storeVisibleOnly = prefs.getBoolean(PrefsStore.Keys.STORE_VISIBLE_ONLY) ?: false
        tmdbKey = prefs.getString(PrefsStore.Keys.TMDB_API_KEY).orEmpty()
        val savedColors = prefs.getStringList(PrefsStore.Keys.COLOR_ACTIONS) ?: emptyList()
        colors = COLOR_KEYS.mapIndexed { index, key ->
            key to savedColors.getOrNull(index).takeIf { COLOR_ACTIONS.containsKey(it) }.orEmpty()
                .ifEmpty { "none" }
        }.toMap()
        hiddenLive = (prefs.getStringList(PrefsStore.Keys.LIVE_HIDDEN) ?: emptyList()).toSet()
        liveCats = sortByOrder(state.allLiveCats, prefs.getStringList(PrefsStore.Keys.LIVE_ORDER) ?: emptyList()) { it.categoryId }
        hiddenVod = (prefs.getStringList(PrefsStore.Keys.VOD_HIDDEN) ?: emptyList()).toSet()
        vodCats = sortByOrder(state.allVodCats, prefs.getStringList(PrefsStore.Keys.VOD_ORDER) ?: emptyList()) { it.categoryId }
        hiddenSeries = (prefs.getStringList(PrefsStore.Keys.SERIES_HIDDEN) ?: emptyList()).toSet()
        seriesCats = sortByOrder(state.allSeriesCats, prefs.getStringList(PrefsStore.Keys.SERIES_ORDER) ?: emptyList()) { it.categoryId }
    }

    // Si el catálogo termina de cargar (o se recarga) con Ajustes ya abierto,
    // refrescar las listas para no operar sobre una fotografía obsoleta.
    LaunchedEffect(state.allLiveCats, state.allVodCats, state.allSeriesCats) {
        liveCats = sortByOrder(state.allLiveCats, prefs.getStringList(PrefsStore.Keys.LIVE_ORDER) ?: emptyList()) { it.categoryId }
        vodCats = sortByOrder(state.allVodCats, prefs.getStringList(PrefsStore.Keys.VOD_ORDER) ?: emptyList()) { it.categoryId }
        seriesCats = sortByOrder(state.allSeriesCats, prefs.getStringList(PrefsStore.Keys.SERIES_ORDER) ?: emptyList()) { it.categoryId }
    }

    fun autoSaveCredentials() {
        scope.launch {
            credentials.save(username.trim(), password.trim(), url.trim())
        }
    }

    fun saveAndDone() {
        scope.launch {
            // NonCancellable: al navegar fuera, el scope de composición se
            // cancela y podía perder el último guardado de credenciales.
            withContext(NonCancellable) {
                credentials.save(username.trim(), password.trim(), url.trim())
                prefs.setStringList(PrefsStore.Keys.COLOR_ACTIONS, COLOR_KEYS.map { colors[it] ?: "none" })
                val changed = vm.updateClientIfCredentialsChanged()
                // Solo una cuenta distinta invalida los datos; el resto usa la caché.
                vm.load(forceRefresh = changed)
            }
            onDone()
        }
    }

    BackHandler(onBack = ::saveAndDone)

    CompositionLocalProvider(LocalHighContrast provides contrast) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.ink),
            contentPadding = PaddingValues(20.dp),
        ) {
            item {
                SectionHeader("Credenciales IPTV")
                Spacer(Modifier.height(12.dp))
                SettingsCard {
                    CredentialField(
                        value = url,
                        onValueChange = {
                            url = it
                            autoSaveCredentials()
                        },
                        label = "URL Servidor",
                        icon = Icons.Filled.Link,
                        focusRequester = urlFocus,
                    )
                    Spacer(Modifier.height(12.dp))
                    CredentialField(
                        value = username,
                        onValueChange = {
                            username = it
                            autoSaveCredentials()
                        },
                        label = "Usuario",
                        icon = Icons.Filled.Person,
                    )
                    Spacer(Modifier.height(12.dp))
                    CredentialField(
                        value = password,
                        onValueChange = {
                            password = it
                            autoSaveCredentials()
                        },
                        label = "Contraseña",
                        icon = Icons.Filled.Lock,
                        password = true,
                    )
                }
            }

            item {
                Spacer(Modifier.height(28.dp))
                SectionHeader("Apariencia y accesos")
                Spacer(Modifier.height(12.dp))
                SettingsCard {
                    Text(
                        text = "Tamaño de tarjetas",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    val steps = listOf(0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.3f)
                    val scaleTvFocus = rememberTvFocus()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (scaleTvFocus.focused) AppColors.focusFill else Color.Transparent)
                            .border(
                                2.dp,
                                if (scaleTvFocus.focused) AppColors.mint else Color.Transparent,
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        Slider(
                            value = scale,
                            onValueChange = {
                                val target =
                                    steps.minByOrNull { step -> kotlin.math.abs(step - it) } ?: 1f
                                if (target != scale) {
                                    scale = target
                                    scope.launch {
                                        prefs.setString(
                                            PrefsStore.Keys.CARD_SCALE,
                                            scale.toString(),
                                        )
                                    }
                                }
                            },
                            valueRange = 0.8f..1.3f,
                            steps = 4,
                            modifier = Modifier
                                .focusRequester(scaleFocus)
                                .focusable(interactionSource = scaleTvFocus.interaction)
                                .onPreviewKeyEvent { event ->
                                    if (!event.isKeyDown()) return@onPreviewKeyEvent false
                                    val currentIndex = steps.indexOf(scale)
                                    when (event.key) {
                                        Key.DirectionLeft -> {
                                            if (currentIndex > 0) {
                                                scale = steps[currentIndex - 1]
                                                scope.launch {
                                                    prefs.setString(
                                                        PrefsStore.Keys.CARD_SCALE,
                                                        scale.toString(),
                                                    )
                                                }
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        Key.DirectionRight -> {
                                            if (currentIndex < steps.lastIndex) {
                                                scale = steps[currentIndex + 1]
                                                scope.launch {
                                                    prefs.setString(
                                                        PrefsStore.Keys.CARD_SCALE,
                                                        scale.toString(),
                                                    )
                                                }
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        else -> false
                                    }
                                },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Densidad de cuadrícula",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    CycleRow(
                        value = when (density) {
                            1 -> "Cómoda"
                            2 -> "Densa"
                            else -> "Automática"
                        },
                        onCycle = {
                            density = when (density) {
                                0 -> 1
                                1 -> 2
                                else -> 0
                            }
                            scope.launch {
                                prefs.setString(
                                    PrefsStore.Keys.GRID_DENSITY,
                                    density.toString(),
                                )
                            }
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    SwitchRow(
                        title = "Modo alto contraste",
                        checked = contrast,
                        onCheckedChange = {
                            contrast = it
                            scope.launch { prefs.setBoolean(PrefsStore.Keys.HIGH_CONTRAST, it) }
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Botones de color del mando",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    COLOR_KEYS.forEach { key ->
                        ColorActionRow(
                            colorKey = key,
                            action = colors[key] ?: "none",
                            onCycle = {
                                val keys = COLOR_ACTIONS.keys.toList()
                                val next =
                                    keys[(keys.indexOf(colors[key] ?: "none") + 1) % keys.size]
                                colors = colors + (key to next)
                                scope.launch {
                                    prefs.setStringList(
                                        PrefsStore.Keys.COLOR_ACTIONS,
                                        COLOR_KEYS.map { colors[it] ?: "none" },
                                    )
                                }
                            },
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(28.dp))
                SectionHeader("Actualización Automática")
                Spacer(Modifier.height(12.dp))
                CycleRow(
                    value = when (refreshDays) {
                        1 -> "Cada 1 Día"
                        3 -> "Cada 3 Días"
                        7 -> "Cada 7 Días"
                        else -> "Nunca"
                    },
                    onCycle = {
                        refreshDays = when (refreshDays) {
                            0 -> 1
                            1 -> 3
                            3 -> 7
                            else -> 0
                        }
                        scope.launch {
                            prefs.setString(PrefsStore.Keys.AUTO_REFRESH_DAYS, refreshDays.toString())
                            prefs.setString(
                                PrefsStore.Keys.AUTO_REFRESH_MINUTES,
                                (refreshDays * 1440).toString(),
                            )
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    title = "Guardar solo el contenido visible",
                    subtitle = "Reduce el almacenamiento local usando las categorías mostradas.",
                    checked = storeVisibleOnly,
                    onCheckedChange = {
                        storeVisibleOnly = it
                        scope.launch { prefs.setBoolean(PrefsStore.Keys.STORE_VISIBLE_ONLY, it) }
                    },
                )
            }

            item {
                Spacer(Modifier.height(28.dp))
                SectionHeader("Pósters faltantes")
                Spacer(Modifier.height(12.dp))
                SettingsCard {
                    Text(
                        text = "Cuando una película o serie viene sin cartelera, se busca en " +
                            "iTunes (sin registro). Si añades una API key gratuita de TMDB, se " +
                            "intentará ahí primero (mejor cobertura). Estas consultas no usan tu " +
                            "panel IPTV.",
                        color = subtleTextColor(),
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    CredentialField(
                        value = tmdbKey,
                        onValueChange = {
                            tmdbKey = it
                            scope.launch {
                                prefs.setString(PrefsStore.Keys.TMDB_API_KEY, it.trim())
                            }
                        },
                        label = "TMDB API key (opcional)",
                        icon = Icons.Filled.Image,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            scope.launch { PosterResolver.clearCache() }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.accent,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Vaciar caché de pósters")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Útil tras añadir o cambiar la key de TMDB para reintentar los " +
                            "títulos que fallaron.",
                        color = subtleTextColor(),
                        fontSize = 12.sp,
                    )
                }
            }

            item {
                Spacer(Modifier.height(28.dp))
                SectionHeader("Gestión de Categorías")
                Spacer(Modifier.height(12.dp))
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = AppColors.panel,
                    contentColor = AppColors.mint,
                ) {
                    listOf("En Vivo", "Películas", "Series").forEachIndexed { index, title ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(title) },
                        )
                    }
                }
            }

            item {
                Box0Height380 {
                    val firstCatFocus = remember(tab) { FocusRequester() }
                    var isInitialTabFocus by remember { mutableStateOf(true) }

                    LaunchedEffect(tab) {
                        if (isInitialTabFocus) {
                            isInitialTabFocus = false
                            return@LaunchedEffect
                        }
                        // Pequeño delay para asegurar que la lista esté lista
                        kotlinx.coroutines.delay(100)
                        try {
                            firstCatFocus.tryRequestFocus()
                        } catch (_: Exception) {
                        }
                    }

                    val onMoveTab: (Int) -> Unit = { dir ->
                        tab = when {
                            tab + dir < 0 -> 2
                            tab + dir > 2 -> 0
                            else -> tab + dir
                        }
                    }

                    when (tab) {
                        0 -> CategoryTab(
                            items = liveCats.map { it.categoryId to it.categoryName },
                            hidden = hiddenLive,
                            onToggle = { id, visible ->
                                hiddenLive = if (visible) hiddenLive - id else hiddenLive + id
                                scope.launch {
                                    prefs.setStringList(PrefsStore.Keys.LIVE_HIDDEN, hiddenLive.toList())
                                    prefs.setStringList(
                                        PrefsStore.Keys.LIVE_ORDER,
                                        liveCats.map { it.categoryId },
                                    )
                                }
                            },
                            onShowAll = {
                                hiddenLive = emptySet()
                                scope.launch {
                                    prefs.setStringList(PrefsStore.Keys.LIVE_HIDDEN, emptyList())
                                    prefs.setStringList(
                                        PrefsStore.Keys.LIVE_ORDER,
                                        liveCats.map { it.categoryId },
                                    )
                                }
                            },
                            onHideAll = {
                                hiddenLive = liveCats.map { it.categoryId }.toSet()
                                scope.launch {
                                    prefs.setStringList(PrefsStore.Keys.LIVE_HIDDEN, hiddenLive.toList())
                                    prefs.setStringList(
                                        PrefsStore.Keys.LIVE_ORDER,
                                        liveCats.map { it.categoryId },
                                    )
                                }
                            },
                            onMoveTab = onMoveTab,
                            firstItemFocus = firstCatFocus,
                        )
                        1 -> CategoryTab(
                            items = vodCats.map { it.categoryId to it.categoryName },
                            hidden = hiddenVod,
                            onToggle = { id, visible ->
                                hiddenVod = if (visible) hiddenVod - id else hiddenVod + id
                                scope.launch {
                                    prefs.setStringList(PrefsStore.Keys.VOD_HIDDEN, hiddenVod.toList())
                                    prefs.setStringList(
                                        PrefsStore.Keys.VOD_ORDER,
                                        vodCats.map { it.categoryId },
                                    )
                                }
                            },
                            onShowAll = {
                                hiddenVod = emptySet()
                                scope.launch {
                                    prefs.setStringList(PrefsStore.Keys.VOD_HIDDEN, emptyList())
                                    prefs.setStringList(
                                        PrefsStore.Keys.VOD_ORDER,
                                        vodCats.map { it.categoryId },
                                    )
                                }
                            },
                            onHideAll = {
                                hiddenVod = vodCats.map { it.categoryId }.toSet()
                                scope.launch {
                                    prefs.setStringList(PrefsStore.Keys.VOD_HIDDEN, hiddenVod.toList())
                                    prefs.setStringList(
                                        PrefsStore.Keys.VOD_ORDER,
                                        vodCats.map { it.categoryId },
                                    )
                                }
                            },
                            onMoveTab = onMoveTab,
                            firstItemFocus = firstCatFocus,
                        )
                        else -> CategoryTab(
                            items = seriesCats.map { it.categoryId to it.categoryName },
                            hidden = hiddenSeries,
                            onToggle = { id, visible ->
                                hiddenSeries = if (visible) hiddenSeries - id else hiddenSeries + id
                                scope.launch {
                                    prefs.setStringList(
                                        PrefsStore.Keys.SERIES_HIDDEN,
                                        hiddenSeries.toList(),
                                    )
                                    prefs.setStringList(
                                        PrefsStore.Keys.SERIES_ORDER,
                                        seriesCats.map { it.categoryId },
                                    )
                                }
                            },
                            onShowAll = {
                                hiddenSeries = emptySet()
                                scope.launch {
                                    prefs.setStringList(PrefsStore.Keys.SERIES_HIDDEN, emptyList())
                                    prefs.setStringList(
                                        PrefsStore.Keys.SERIES_ORDER,
                                        seriesCats.map { it.categoryId },
                                    )
                                }
                            },
                            onHideAll = {
                                hiddenSeries = seriesCats.map { it.categoryId }.toSet()
                                scope.launch {
                                    prefs.setStringList(
                                        PrefsStore.Keys.SERIES_HIDDEN,
                                        hiddenSeries.toList(),
                                    )
                                    prefs.setStringList(
                                        PrefsStore.Keys.SERIES_ORDER,
                                        seriesCats.map { it.categoryId },
                                    )
                                }
                            },
                            onMoveTab = onMoveTab,
                            firstItemFocus = firstCatFocus,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun <T> sortByOrder(all: List<T>, order: List<String>, idOf: (T) -> String): List<T> {
    if (order.isEmpty()) return all
    return all.sortedWith { a, b ->
        val ia = order.indexOf(idOf(a))
        val ib = order.indexOf(idOf(b))
        when {
            ia == -1 && ib == -1 -> 0
            ia == -1 -> 1
            ib == -1 -> -1
            else -> ia.compareTo(ib)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(text = title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.material3.Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.panel),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun CredentialField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    focusRequester: FocusRequester? = null,
    autofocus: Boolean = false,
    password: Boolean = false,
) {
    LaunchedEffect(autofocus) {
        if (autofocus) focusRequester?.tryRequestFocus()
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = AppColors.mint) },
        singleLine = true,
                    visualTransformation = if (password) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        textStyle = TextStyle(fontSize = 14.sp, color = Color.White),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = AppColors.panel,
            unfocusedContainerColor = AppColors.panel,
            focusedBorderColor = AppColors.accent,
            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
            focusedLabelColor = AppColors.mint,
            unfocusedLabelColor = AppColors.mutedLabel,
            cursorColor = AppColors.mint,
        ),
    )
}

@Composable
private fun CycleRow(value: String, onCycle: () -> Unit) {
    val focus = rememberTvFocus()
    val focused = focus.focused
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) AppColors.focusFill else Color.Transparent)
            .border(
                2.dp,
                if (focused) AppColors.mint else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .focusable(interactionSource = focus.interaction)
            .tvPress(fireOnDown = true, onTap = onCycle)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = value, color = Color.White, fontSize = 16.sp)
        Text(text = "OK cambia", color = subtleTextColor(), fontSize = 12.sp)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // El mando se detiene en la fila, no en el interruptor: el halo de foco de
    // Material apenas se veía sobre el fondo y no sabías qué estabas pulsando.
    val focus = rememberTvFocus()
    val focused = focus.focused
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) AppColors.focusFill else Color.Transparent)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) AppColors.mint else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .focusable(interactionSource = focus.interaction)
            .tvPress(fireOnDown = true, onTap = { onCheckedChange(!checked) })
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 16.sp)
            if (subtitle != null) {
                Text(text = subtitle, color = subtleTextColor(), fontSize = 13.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = novaSwitchColors(),
            modifier = Modifier.focusProperties { canFocus = false },
        )
    }
}

/**
 * El interruptor por defecto heredaba el `onPrimary` del tema: la pastilla
 * quedaba casi negra sobre la pista azul y se leía como un color ajeno a la
 * app. Pista de acento y pastilla blanca en ambos estados.
 */
@Composable
private fun novaSwitchColors() = SwitchDefaults.colors(
    checkedTrackColor = AppColors.accent,
    checkedThumbColor = Color.White,
    checkedBorderColor = AppColors.accent,
    uncheckedTrackColor = AppColors.panel,
    uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
    uncheckedBorderColor = Color.White.copy(alpha = 0.25f),
)

@Composable
private fun ColorActionRow(colorKey: String, action: String, onCycle: () -> Unit) {
    val focus = rememberTvFocus()
    val focused = focus.focused
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) AppColors.focusFill else Color.Transparent)
            .border(
                2.dp,
                if (focused) AppColors.mint else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .focusable(interactionSource = focus.interaction)
            .tvPress(fireOnDown = true, onTap = onCycle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Circle, contentDescription = null, tint = dotColor(colorKey), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text(text = "Botón $colorKey", color = Color.White, fontSize = 16.sp)
        }
        Text(
            text = COLOR_ACTIONS[action] ?: action,
            color = subtleTextColor(),
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun Box0Height380(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.panel)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
    ) {
        content()
    }
}

@Composable
private fun CategoryTab(
    items: List<Pair<String, String>>,
    hidden: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onShowAll: () -> Unit,
    onHideAll: () -> Unit,
    onMoveTab: (Int) -> Unit,
    firstItemFocus: FocusRequester,
) {
    // The category list is nested inside the settings LazyColumn. Consume the
    // leftover scroll at its edges so reaching the top/bottom does not move
    // the whole settings screen.
    val categoryNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = available

            // No tragar el fling: si se consumiera, la página exterior de
            // Ajustes no podría seguir haciendo scroll con el foco dentro.
            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity = Velocity.Zero
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(16.dp, 8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                Button(
                    onClick = onShowAll,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.mint,
                        contentColor = AppColors.ink,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Mostrar Todos", fontWeight = FontWeight.W700)
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                Button(
                    onClick = onHideAll,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.panel,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Ocultar Todos")
                }
            }
        }
        androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(categoryNestedScroll),
        ) {
            itemsIndexed(items, key = { _, item -> item.first }) { index, item ->
                val id = item.first
                val name = item.second
                val visible = !hidden.contains(id)
                val focus = rememberTvFocus()
                val focused = focus.focused
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (focused) AppColors.focusFill else Color.Transparent)
                        .border(2.dp, if (focused) AppColors.mint else Color.Transparent, RoundedCornerShape(8.dp))
                        .then(if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier)
                        .focusable(interactionSource = focus.interaction)
                        .tvPress(fireOnDown = true, onTap = { onToggle(id, !visible) })
                        .onPreviewKeyEvent { event ->
                            if (!event.isKeyDown()) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionLeft -> {
                                    onMoveTab(-1)
                                    true
                                }
                                Key.DirectionRight -> {
                                    onMoveTab(1)
                                    true
                                }
                                else -> false
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Filled.Visibility, 
                            contentDescription = null, 
                            tint = if (visible) AppColors.mint else subtleTextColor()
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(text = name, color = Color.White, maxLines = 1, fontSize = 14.sp)
                    }
                    Switch(checked = visible, onCheckedChange = null, colors = novaSwitchColors())
                }
            }
        }
    }
}
