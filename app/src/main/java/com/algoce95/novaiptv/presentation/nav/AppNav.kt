package com.algoce95.novaiptv.presentation.nav

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.algoce95.novaiptv.R
import com.algoce95.novaiptv.core.di.AppContainer
import com.algoce95.novaiptv.core.storage.PrefsStore
import com.algoce95.novaiptv.core.theme.LocalHighContrast
import com.algoce95.novaiptv.core.di.vmFactory
import com.algoce95.novaiptv.core.theme.AppColors
import com.algoce95.novaiptv.data.model.Parsers
import com.algoce95.novaiptv.presentation.auth.LoginScreen
import com.algoce95.novaiptv.presentation.home.EpgGuideScreen
import com.algoce95.novaiptv.presentation.home.HomeCatalogViewModel
import com.algoce95.novaiptv.presentation.home.HomeScreen
import com.algoce95.novaiptv.presentation.home.MovieDetailScreen
import com.algoce95.novaiptv.presentation.home.SearchResultsScreen
import com.algoce95.novaiptv.presentation.home.SeriesDetailScreen
import com.algoce95.novaiptv.presentation.player.PlayerScreen
import com.algoce95.novaiptv.presentation.settings.SettingsScreen
import org.json.JSONObject

/** Rutas (paridad con '/login' y '/home' de Flutter). */
object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val HOME = "home"
    const val MOVIE = "movie"
    const val SERIES = "series"
    const val PLAYER = "player"
    const val EPG = "epg"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    const val ARG_JSON = "json"
    const val ARG_EPISODE = "episodeId"

    fun movie(movieJson: String) = "$MOVIE?$ARG_JSON=${Uri.encode(movieJson)}"
    fun series(seriesJson: String, episodeId: String? = null): String {
        var route = "$SERIES?$ARG_JSON=${Uri.encode(seriesJson)}"
        if (episodeId != null) route += "&$ARG_EPISODE=${Uri.encode(episodeId)}"
        return route
    }
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    // El alto contraste debe regir también en fichas, guía, búsqueda y
    // reproductor: hasta ahora solo se aplicaba dentro de HOME y Ajustes.
    // Se lee del almacén directamente: AppContainer aún no está inicializado
    // mientras vive la pantalla de arranque.
    val context = LocalContext.current
    val prefs = remember { PrefsStore.get(context) }
    val highContrast by prefs.booleanFlow(PrefsStore.Keys.HIGH_CONTRAST)
        .collectAsState(initial = false)
    CompositionLocalProvider(LocalHighContrast provides highContrast) {
        NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onDone = { authenticated ->
                    navController.navigate(if (authenticated) Routes.HOME else Routes.LOGIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(navController = navController)
        }
        composable(
            route = "${Routes.MOVIE}?${Routes.ARG_JSON}={${Routes.ARG_JSON}}",
            arguments = listOf(
                navArgument(Routes.ARG_JSON) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val movie = remember(backStackEntry) {
                backStackEntry.arguments?.getString(Routes.ARG_JSON)
                    ?.let { runCatching { Parsers.parseVodMovie(JSONObject(it)) }.getOrNull() }
            }
            if (movie == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                MovieDetailScreen(
                    movie = movie,
                    onPlay = { navController.navigate(Routes.PLAYER) },
                )
            }
        }
        composable(
            route = "${Routes.SERIES}?${Routes.ARG_JSON}={${Routes.ARG_JSON}}&" +
                "${Routes.ARG_EPISODE}={${Routes.ARG_EPISODE}}",
            arguments = listOf(
                navArgument(Routes.ARG_JSON) { type = NavType.StringType },
                navArgument(Routes.ARG_EPISODE) {
                    type = NavType.StringType
                    nullable = true
                },
            ),
        ) { backStackEntry ->
            val series = remember(backStackEntry) {
                backStackEntry.arguments?.getString(Routes.ARG_JSON)
                    ?.let { runCatching { Parsers.parseSeries(JSONObject(it)) }.getOrNull() }
            }
            if (series == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                SeriesDetailScreen(
                    series = series,
                    initialEpisodeId = backStackEntry.arguments
                        ?.getString(Routes.ARG_EPISODE),
                    onPlay = { navController.navigate(Routes.PLAYER) },
                )
            }
        }
        composable(Routes.PLAYER) {
            PlayerScreen(onClose = { navController.popBackStack() })
        }
        composable(Routes.EPG) {
            EpgGuideScreen(
                onPlay = { navController.navigate(Routes.PLAYER) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SEARCH) {
            SearchResultsScreen(
                query = AppContainer.searchSnapshot.query,
                onPlayMovie = { movie ->
                    navController.navigate(Routes.movie(movie.toCacheJson().toString()))
                },
                onPlaySeries = { series ->
                    navController.navigate(Routes.series(series.toCacheJson().toString()))
                },
                onPlay = { navController.navigate(Routes.PLAYER) },
            )
        }
        composable(Routes.SETTINGS) {
            val homeEntry = remember {
                try {
                    navController.getBackStackEntry(Routes.HOME)
                } catch (_: Exception) {
                    null
                }
            }
            if (homeEntry == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                val homeVm: HomeCatalogViewModel = viewModel(
                    viewModelStoreOwner = homeEntry,
                    factory = vmFactory { HomeCatalogViewModel() },
                )
                SettingsScreen(
                    vm = homeVm,
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
    }
}

/** Arranque: credenciales, migración y decisión de ruta (equivale a main.dart). */
@Composable
private fun SplashScreen(onDone: (Boolean) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        onDone(AppContainer.ensureInitialized(context))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.ink),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(82.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "NOVA IPTV",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.W800,
                letterSpacing = 1.1.sp,
            )
            Spacer(Modifier.height(26.dp))
            CircularProgressIndicator(color = AppColors.accent)
        }
    }
}

// TODO(F2-F5): sustituir por las pantallas reales.
@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.ink),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "NOVA IPTV · $title",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
