@file:OptIn(UnstableApi::class)

package com.hms.player

import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun PlayerScreen(vm: AppVM, req: PlayerRequest) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ctl = remember(req) { PlayerCtl(ctx, vm, req, scope) }
    val rootFr = remember { FocusRequester() }
    val playFr = remember { FocusRequester() }
    var show by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var focusPlay by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var drag by remember { mutableStateOf<Float?>(null) }
    val appear = remember { Animatable(0f) }

    fun poke() { interaction++ }
    fun reveal(byKey: Boolean) { show = true; poke(); if (byKey) focusPlay++ }

    BackHandler(true) { ctl.close() }

    DisposableEffect(ctl) {
        ctl.start()
        onDispose { ctl.release() }
    }
    LaunchedEffect(ctl) { while (true) { ctl.tick(); delay(500) } }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(280)) }
    LaunchedEffect(Unit) { runCatching { rootFr.requestFocus() } }
    LaunchedEffect(show) { if (!show) runCatching { rootFr.requestFocus() } }
    LaunchedEffect(focusPlay) { if (focusPlay > 0) { delay(90); runCatching { playFr.requestFocus() } } }
    // auto-hide controls while playing
    LaunchedEffect(show, interaction, ctl.isPlaying) {
        if (show && ctl.isPlaying) { delay(4_500); show = false }
    }

    // immersive + keep screen on + pause when app goes to background
    DisposableEffect(Unit) {
        val act = ctx.findActivity()
        val w = act?.window
        w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val c = w?.let { WindowCompat.getInsetsController(it, it.decorView) }
        c?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c?.hide(WindowInsetsCompat.Type.systemBars())
        val lc = (act as? LifecycleOwner)?.lifecycle
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) ctl.suspendStream() }
        lc?.addObserver(ob)
        onDispose {
            lc?.removeObserver(ob)
            w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = appear.value; val s = 0.94f + 0.06f * appear.value; scaleX = s; scaleY = s }
                .background(Color.Black)
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    poke()
                    val live = ctl.cur.live
                    when (ev.key) {
                        Key.MediaPlayPause -> { ctl.toggle(); true }
                        Key.MediaPlay -> { ctl.playNow(); true }
                        Key.MediaPause -> { ctl.player.pause(); true }
                        Key.MediaStop -> { ctl.close(); true }
                        Key.MediaRewind -> { if (!live) ctl.seekBy(-30_000); true }
                        Key.MediaFastForward -> { if (!live) ctl.seekBy(30_000); true }
                        Key.MediaNext, Key.ChannelUp -> { ctl.next(); true }
                        Key.MediaPrevious, Key.ChannelDown -> { ctl.prev(); true }
                        else -> if (show) false else when (ev.key) {
                            // controls hidden: remote acts directly
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (!live) ctl.toggle(); reveal(true); true }
                            Key.DirectionLeft -> { if (!live) ctl.seekBy(-10_000); reveal(true); true }
                            Key.DirectionRight -> { if (!live) ctl.seekBy(10_000); reveal(true); true }
                            Key.DirectionUp -> { if (live) ctl.prev(); reveal(true); true }
                            Key.DirectionDown -> { if (live) ctl.next(); reveal(true); true }
                            else -> false
                        }
                    }
                }
                .focusRequester(rootFr)
                .focusable()
                .pointerInput(ctl) {
                    detectTapGestures(
                        onTap = { show = !show; poke() },
                        onDoubleTap = { o ->
                            if (!ctl.cur.live) {
                                val fwd = o.x > size.width / 2
                                ctl.seekBy(if (fwd) 10_000 else -10_000)
                                ctl.flash(if (fwd) "+10 ث" else "-10 ث")
                            }
                        }
                    )
                }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    PlayerView(c).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        useController = false
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        this.player = ctl.player
                        resizeMode = ctl.resize
                    }
                },
                update = { it.resizeMode = ctl.resize }
            )

            // ---------- controls ----------
            AnimatedVisibility(show, Modifier.fillMaxSize(), enter = fadeIn(tween(180)), exit = fadeOut(tween(260))) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(110.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .78f), Color.Transparent))))
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(140.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f)))))

                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
                    // top bar
                    Row(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") { ctl.close() }
                        Text(
                            ctl.cur.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        if (ctl.videoSize.isNotEmpty()) Text(ctl.videoSize, color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                        IconBtn(Icons.Default.Audiotrack, "الصوت") { dialog = "audio" }
                        IconBtn(Icons.Default.Subtitles, "الترجمة") { dialog = "text" }
                        IconBtn(Icons.Default.AspectRatio, "حجم الصورة") { ctl.cycleResize() }
                        if (!ctl.cur.live) IconBtn(Icons.Default.Speed, "السرعة") { dialog = "speed" }
                    }

                    // center transport
                    Row(
                        Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp)
                    ) {
                        if (ctl.hasPrev) IconBtn(Icons.Default.SkipPrevious, "السابق", size = 54.dp) { ctl.prev() }
                        if (!ctl.cur.live) IconBtn(Icons.Default.Replay10, "ارجع 10 ثواني", size = 60.dp) { ctl.seekBy(-10_000) }
                        IconBtn(
                            if (ctl.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            "تشغيل / إيقاف", size = 78.dp, filled = true, focusRequester = playFr
                        ) { ctl.toggle() }
                        if (!ctl.cur.live) IconBtn(Icons.Default.Forward10, "قدّم 10 ثواني", size = 60.dp) { ctl.seekBy(10_000) }
                        if (ctl.hasNext) IconBtn(Icons.Default.SkipNext, "التالي", size = 54.dp) { ctl.next() }
                    }

                    // bottom bar
                    Row(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (ctl.cur.live) {
                            Surface(shape = RoundedCornerShape(8.dp), color = Danger) {
                                Text("● مباشر", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text("${ctl.index + 1} / ${ctl.req.items.size}", color = Color.White.copy(alpha = .75f), fontSize = 13.sp)
                        } else {
                            Text(fmtTime(drag?.toLong() ?: ctl.pos), color = Color.White, fontSize = 13.sp, modifier = Modifier.width(56.dp))
                            Slider(
                                value = (drag ?: ctl.pos.toFloat()).coerceIn(0f, ctl.dur.toFloat().coerceAtLeast(1f)),
                                onValueChange = { drag = it; poke() },
                                onValueChangeFinished = { drag?.let { ctl.seekTo(it.toLong()) }; drag = null },
                                valueRange = 0f..ctl.dur.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Orange, activeTrackColor = Orange, inactiveTrackColor = Color.White.copy(alpha = .3f)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Text(if (ctl.dur > 0) fmtTime(ctl.dur) else "--:--", color = Color.White, fontSize = 13.sp,
                                modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
                        }
                    }
                    }
                }
            }

            // ---------- overlays ----------
            ctl.toast?.let { Pill(it, Modifier.align(Alignment.TopCenter).padding(top = 72.dp)) }
            if (ctl.buffering && ctl.message == null && !ctl.failed) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Orange, strokeWidth = 4.dp)
            }
            ctl.message?.let { m ->
                if (ctl.failed) {
                    Surface(
                        Modifier.align(Alignment.Center).fillMaxWidth(0.6f), shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = .85f), contentColor = Color.White
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(m, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HmsButton("إعادة المحاولة") { ctl.retryNow() }
                                    HmsButton("رجوع", primary = false) { ctl.close() }
                                }
                            }
                        }
                    }
                } else {
                    Pill(m, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
                }
            }
        }

        when (dialog) {
            "audio" -> TrackDialog(ctl.player, C.TRACK_TYPE_AUDIO, "الصوت", false) { dialog = null }
            "text" -> TrackDialog(ctl.player, C.TRACK_TYPE_TEXT, "الترجمة", true) { dialog = null }
            "speed" -> SpeedDialog(ctl.speed, { ctl.changeSpeed(it); dialog = null }) { dialog = null }
        }
    }
}

private fun trackLabel(f: Format, n: Int): String {
    val lang = f.language?.takeIf { it != "und" }
        ?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale("ar")) }
    val ch = f.channelCount.takeIf { it > 0 }?.let { "${it}ch" }
    return listOfNotNull(f.label, lang, ch).joinToString(" • ").ifBlank { "مسار $n" }
}

@Composable
private fun TrackDialog(player: Player, type: Int, title: String, allowOff: Boolean, onDismiss: () -> Unit) {
    val groups = remember(player) { player.currentTracks.groups.filter { it.type == type && it.isSupported } }
    val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(type)
    DialogShell(title, onDismiss, 0.45f) {
        if (groups.isEmpty() && !allowOff) {
            Text("مفيش مسارات متاحة", color = TextMuted, fontSize = 14.sp)
        } else {
            LazyColumn(Modifier.heightIn(max = 230.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (allowOff) item {
                    OptionRow("إيقاف", disabled || groups.none { g -> (0 until g.length).any { g.isTrackSelected(it) } }) {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(type, true).build()
                        onDismiss()
                    }
                }
                var n = 0
                groups.forEach { g ->
                    for (ti in 0 until g.length) {
                        n++
                        val label = trackLabel(g.getTrackFormat(ti), n)
                        val on = g.isTrackSelected(ti) && !disabled
                        item {
                            OptionRow(label, on) {
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(type, false)
                                    .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, ti))
                                    .build()
                                onDismiss()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedDialog(current: Float, onPick: (Float) -> Unit, onDismiss: () -> Unit) {
    DialogShell("سرعة التشغيل", onDismiss, 0.35f) {
        LazyColumn(Modifier.heightIn(max = 230.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)) { s ->
                OptionRow(if (s == 1f) "عادية (1x)" else "${s}x", s == current) { onPick(s) }
            }
        }
    }
}

@Composable
private fun OptionRow(text: String, selected: Boolean, onClick: () -> Unit) {
    FocusBox(Modifier.fillMaxWidth().height(42.dp), RoundedCornerShape(12.dp), onClick) {
        Box(Modifier.fillMaxSize().background(if (selected) Orange.copy(alpha = .25f) else Surface2))
        Text(
            (if (selected) "✓  " else "") + text, color = if (selected) OrangeSoft else TextMain, fontSize = 14.sp,
            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp), maxLines = 1
        )
    }
}
