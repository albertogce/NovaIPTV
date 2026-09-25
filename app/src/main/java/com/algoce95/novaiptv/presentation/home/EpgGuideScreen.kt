package com.algoce95.novaiptv.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algoce95.novaiptv.core.di.AppContainer
import com.algoce95.novaiptv.core.di.PlayerSession
import com.algoce95.novaiptv.core.di.QueueItem
import com.algoce95.novaiptv.core.theme.AppColors
import com.algoce95.novaiptv.core.theme.NovaType
import com.algoce95.novaiptv.core.theme.subtleTextColor
import com.algoce95.novaiptv.data.model.EpgProgram
import com.algoce95.novaiptv.data.model.LiveChannel
import com.algoce95.novaiptv.presentation.tv.ChannelLogo
import com.algoce95.novaiptv.presentation.tv.EmptyState
import com.algoce95.novaiptv.presentation.tv.NovaButton
import com.algoce95.novaiptv.presentation.tv.rememberTvFocus
import com.algoce95.novaiptv.presentation.tv.tryRequestFocus
import com.algoce95.novaiptv.presentation.tv.tvFocusScale
import com.algoce95.novaiptv.presentation.tv.tvPress
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/** Paridad con `EpgGuideScreen` de Flutter (canales ya filtrados por categoría). */
@Composable
fun EpgGuideScreen(onPlay: () -> Unit, onBack: () -> Unit) {
    val channels = remember { AppContainer.epgChannels }
    var selected by remember { mutableStateOf<String?>(null) }
    var programs by remember { mutableStateOf(mapOf<String, List<EpgProgram>>()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun select(channel: LiveChannel) {
        val id = channel.channelId.toString()
        selected = id
        if (programs.containsKey(id)) return
        loading = true
        try {
            val list = requireNotNull(AppContainer.api).getShortEpgPrograms(channel.channelId)
            programs = programs + (id to list)
        } catch (_: Exception) {
            // Sin guía para este canal.
        } finally {
            loading = false
        }
    }

    fun playChannel() {
        val channel = channels.firstOrNull { it.channelId.toString() == selected } ?: return
        val client = requireNotNull(AppContainer.api)
        val queue = channels.map {
            QueueItem(
                streamUrl = client.liveStreamUrl(it.channelId),
                title = it.channelName,
            )
        }
        var index = channels.indexOfFirst { it.channelId == channel.channelId }
        if (index < 0) index = 0
        AppContainer.playerSession = PlayerSession(
            items = queue,
            index = index,
            title = queue[index].title,
            progressId = null,
            isLive = true,
        )
        onPlay()
    }

    LaunchedEffect(channels) {
        if (channels.isNotEmpty()) select(channels.first())
    }
    val firstChannelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstChannelFocus.tryRequestFocus() }

    if (channels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(
                    icon = Icons.Filled.LiveTv,
                    title = "Sin canales",
                    hint = "Vuelve a la vista de canales y elige una categoría.",
                )
                Spacer(Modifier.height(16.dp))
                NovaButton(label = "Volver", onClick = onBack)
            }
        }
        return
    }

    val list = programs[selected] ?: emptyList()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.ink),
    ) {
        LazyColumn(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight(),
        ) {
            itemsIndexed(channels, key = { _, channel -> channel.channelId }) { index, channel ->
                val isSelected = selected == channel.channelId.toString()
                val focus = rememberTvFocus()
                ListItem(
                    headlineContent = {
                        Text(
                            text = channel.channelName,
                            maxLines = 2,
                            lineHeight = 18.sp,
                            style = NovaType.subtitle,
                            color = if (isSelected) AppColors.accentBright else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    leadingContent = {
                        ChannelLogo(
                            url = channel.channelLogo,
                            name = channel.channelName,
                            modifier = Modifier.size(44.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (focus.focused) {
                            AppColors.mint.copy(alpha = 0.16f)
                        } else {
                            Color.Transparent
                        },
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .then(if (index == 0) Modifier.focusRequester(firstChannelFocus) else Modifier)
                        .focusable(interactionSource = focus.interaction)
                        .tvPress(fireOnDown = true, onTap = { scope.launch { select(channel) } })
                        .tvFocusScale(focus.focused, 1.02f),
                )
            }
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.12f)),
        )
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when {
                loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.accent)
                }
                list.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay programación disponible", color = subtleTextColor())
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                ) {
                    items(list) { program ->
                        val start = program.startMs.calendarHM()
                        val live = program.isLive()
                        val focus = rememberTvFocus()
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (live) AppColors.epgLive else AppColors.panel,
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                2.dp,
                                if (focus.focused) {
                                    AppColors.mint
                                } else if (live) {
                                    AppColors.mint.copy(alpha = 0.35f)
                                } else {
                                    Color.White.copy(alpha = 0.07f)
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .focusable(interactionSource = focus.interaction)
                                .tvPress(fireOnDown = true, onTap = ::playChannel)
                                .tvFocusScale(focus.focused),
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = start,
                                        style = NovaType.subtitle.copy(color = AppColors.amber),
                                        modifier = Modifier.width(64.dp),
                                    )
                                    Text(
                                        text = program.title,
                                        style = NovaType.title.copy(color = Color.White),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (live) {
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = "EN EMISIÓN",
                                            style = NovaType.badge.copy(color = AppColors.mint),
                                        )
                                    }
                                }
                                if (live) {
                                    Spacer(Modifier.height(12.dp))
                                    LinearProgressIndicator(
                                        progress = { program.progress().toFloat() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = AppColors.mint,
                                        trackColor = Color.White.copy(alpha = 0.12f),
                                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    )
                                }
                                if (program.description.isNotEmpty()) {
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = program.description,
                                        style = NovaType.meta,
                                        color = subtleTextColor(),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Long.calendarHM(): String {
    val cal = Calendar.getInstance(Locale.getDefault())
    cal.timeInMillis = this
    val h = cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
    val m = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
    return "$h:$m"
}
