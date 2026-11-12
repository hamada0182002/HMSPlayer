@file:OptIn(UnstableApi::class)
@file:Suppress("NewApi") // every PiP call below is guarded by pipSupported() / SDK checks

package com.hms.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon as AndroidIcon
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.PictureInPictureAlt
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
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun PlayerScreen(vm: AppVM, req: PlayerRequest) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ctl = remember(req) { PlayerCtl(ctx, vm, req, scope) }
    val tv = LocalTv.current
    val rootFr = remember { FocusRequester() }
    val playFr = remember { FocusRequester() }
    var show by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var focusPlay by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var seekFocused by remember { mutableStateOf(false) }
    var topFocused by remember { mutableStateOf(false) }
    val topFr = remember { FocusRequester() }
    val appear = remember { Animatable(0f) }

    // ---------- picture-in-picture (phone only) ----------
    val act = remember { ctx.findActivity() }
    val canPip = !tv && pipSupported(ctx)
    var inPip by remember { mutableStateOf(canPip && act?.isInPictureInPictureMode == true) }

    DisposableEffect(canPip, act) {
        val ca = act as? ComponentActivity
        if (!canPip || ca == null) return@DisposableEffect onDispose { }
        val modeL = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        ca.addOnPictureInPictureModeChangedListener(modeL)
        // Android 8-11: enter PiP when the user presses Home while playing (Android 12+ uses auto-enter below)
        val leaveL = Runnable {
            if (Build.VERSION.SDK_INT < 31 && vm.store.autoPip && ctl.isPlaying) enterPip(ca, ctx, ctl.videoRatio, true, true)
        }
        ca.addOnUserLeaveHintListener(leaveL)
        // play / pause button shown on the small PiP window
        val rcv = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { ctl.toggle() }
        }
        val f = IntentFilter(PIP_ACTION)
        if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(rcv, f, Context.RECEIVER_NOT_EXPORTED) else ctx.registerReceiver(rcv, f)
        onDispose {
            ca.removeOnPictureInPictureModeChangedListener(modeL)
            ca.removeOnUserLeaveHintListener(leaveL)
            runCatching { ctx.unregisterReceiver(rcv) }
            // leaving the player: Home must not push the (non-playing) app into PiP any more
            if (Build.VERSION.SDK_INT >= 31) runCatching {
                ca.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build())
            }
        }
    }
    // keep the PiP window shape + play/pause icon + auto-enter flag in sync with the player
    LaunchedEffect(canPip, ctl.isPlaying, ctl.videoRatio) {
        if (canPip && act != null) {
            pipParams(ctx, ctl.videoRatio, ctl.isPlaying, vm.store.autoPip)?.let { runCatching { act.setPictureInPictureParams(it) } }
        }
    }
    // inside the small window: no controls, no dialogs
    LaunchedEffect(inPip) { if (inPip) { dialog = null; show = false } }

    fun poke() { interaction++ }
    fun reveal(byKey: Boolean) { show = true; poke(); if (byKey) focusPlay++ }

    // TV remote: first Back hides the controls (if visible), second Back leaves the channel / movie
    BackHandler(true) { if (tv && show) show = false else ctl.close() }

    DisposableEffect(ctl) {
        ctl.start()
        onDispose { ctl.release() }
    }
    LaunchedEffect(ctl) { while (true) { ctl.tick(); delay(500) } }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(280)) }
    LaunchedEffect(Unit) { runCatching { rootFr.requestFocus() } }
    // controls hidden -> root gets the keys; controls shown (TV) -> focus sits on play/pause,
    // so OK = pause/play and left / right move to the buttons beside it.
    LaunchedEffect(show) {
        if (!show) { topFocused = false; runCatching { rootFr.requestFocus() } }
        else if (tv) { delay(150); runCatching { playFr.requestFocus() } }
    }
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
                    // LIVE: up / down always switch the channel (key repeat ignored so the server isn't hammered)
                    if (live && !topFocused && (ev.key == Key.DirectionUp || ev.key == Key.DirectionDown)) {
                        if (ev.nativeKeyEvent.repeatCount == 0) {
                            if (ev.key == Key.DirectionUp) ctl.prev() else ctl.next()
                            reveal(false)
                        }
                        return@onPreviewKeyEvent true
                    }
                    // LIVE: Menu key = jump to the top buttons (audio / subtitles / size) since up/down change channel
                    if (live && ev.key == Key.Menu) {
                        show = true
                        scope.launch { delay(200); runCatching { topFr.requestFocus() } }
                        return@onPreviewKeyEvent true
                    }
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
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { ctl.toggle(); reveal(true); true }
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
                                ctl.flash((if (fwd) "+10 " else "-10 ") + tr("ث", "s"))
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
                update = { it.resizeMode = if (inPip) AspectRatioFrameLayout.RESIZE_MODE_FIT else ctl.resize }
            )

            // ---------- controls ----------
            AnimatedVisibility(show && !inPip, Modifier.fillMaxSize(), enter = fadeIn(tween(180)), exit = fadeOut(tween(260))) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(110.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .78f), Color.Transparent))))
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(140.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f)))))

                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
                    // top bar
                    Row(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                            .onFocusChanged { topFocused = it.hasFocus },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconBtn(Icons.AutoMirrored.Filled.ArrowBack, tr("رجوع", "Back")) { ctl.close() }
                        Text(
                            ctl.cur.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        if (ctl.videoSize.isNotEmpty()) Text(ctl.videoSize, color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                        IconBtn(Icons.Default.Audiotrack, tr("الصوت", "Audio"), focusRequester = topFr) { dialog = "audio" }
                        IconBtn(Icons.Default.Subtitles, tr("الترجمة", "Subtitles")) { dialog = "text" }
                        IconBtn(Icons.Default.AspectRatio, tr("حجم الصورة", "Aspect ratio")) { ctl.cycleResize() }
                        if (canPip) IconBtn(Icons.Default.PictureInPictureAlt, tr("صورة داخل صورة", "Picture in Picture")) {
                            enterPip(act, ctx, ctl.videoRatio, ctl.isPlaying, vm.store.autoPip)
                        }
                        if (!ctl.cur.live) IconBtn(Icons.Default.Speed, tr("السرعة", "Speed")) { dialog = "speed" }
                    }

                    // center transport
                    Row(
                        Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp)
                    ) {
                        if (ctl.hasPrev) IconBtn(Icons.Default.SkipPrevious, tr("السابق", "Previous"), size = 54.dp) { ctl.prev() }
                        if (!ctl.cur.live) IconBtn(Icons.Default.Replay10, tr("ارجع 10 ثواني", "Back 10 seconds"), size = 60.dp) { ctl.seekBy(-10_000) }
                        IconBtn(
                            if (ctl.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            tr("تشغيل / إيقاف", "Play / Pause"), size = 78.dp, filled = true, focusRequester = playFr
                        ) { ctl.toggle() }
                        if (!ctl.cur.live) IconBtn(Icons.Default.Forward10, tr("قدّم 10 ثواني", "Forward 10 seconds"), size = 60.dp) { ctl.seekBy(10_000) }
                        if (ctl.hasNext) IconBtn(Icons.Default.SkipNext, tr("التالي", "Next"), size = 54.dp) { ctl.next() }
                    }

                    // bottom bar
                    Row(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (ctl.cur.live) {
                            Surface(shape = RoundedCornerShape(8.dp), color = Danger) {
                                Text(tr("● مباشر", "● LIVE"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text("${ctl.index + 1} / ${ctl.req.items.size}", color = Color.White.copy(alpha = .75f), fontSize = 13.sp)
                        } else {
                            Text(fmtTime(drag?.toLong() ?: ctl.pos), color = Color.White, fontSize = 13.sp, modifier = Modifier.width(56.dp))
                            // Seek bar: focus it with the remote (Down from the buttons), then Left/Right = back/forward.
                            // Holding the key speeds it up.
                            Box(
                                Modifier.weight(1f)
                                    .onFocusChanged { seekFocused = it.hasFocus }
                                    .onPreviewKeyEvent { ev ->
                                        if (ev.key != Key.DirectionLeft && ev.key != Key.DirectionRight) return@onPreviewKeyEvent false
                                        if (ev.type == KeyEventType.KeyDown) {
                                            val rc = ev.nativeKeyEvent.repeatCount
                                            val step = when { rc >= 12 -> 60_000L; rc >= 4 -> 30_000L; else -> 10_000L }
                                            val back = ev.key == Key.DirectionLeft
                                            ctl.seekBy(if (back) -step else step)
                                            ctl.flash((if (back) "-" else "+") + (step / 1000) + " " + tr("ث", "s"))
                                        }
                                        true
                                    }
                                    .focusable()
                                    .border(
                                        2.dp,
                                        if (seekFocused) Orange else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 6.dp)
                            ) {
                                Slider(
                                    value = (drag ?: ctl.pos.toFloat()).coerceIn(0f, ctl.dur.toFloat().coerceAtLeast(1f)),
                                    onValueChange = { drag = it; poke() },
                                    onValueChangeFinished = { drag?.let { ctl.seekTo(it.toLong()) }; drag = null },
                                    valueRange = 0f..ctl.dur.toFloat().coerceAtLeast(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Orange, activeTrackColor = Orange, inactiveTrackColor = Color.White.copy(alpha = .3f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Text(if (ctl.dur > 0) fmtTime(ctl.dur) else "--:--", color = Color.White, fontSize = 13.sp,
                                modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
                        }
                    }
                    }
                }
            }

            // ---------- overlays ----------
            if (!inPip) ctl.toast?.let { Pill(it, Modifier.align(Alignment.TopCenter).padding(top = 72.dp)) }
            if (ctl.buffering && ctl.message == null && !ctl.failed) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Orange, strokeWidth = 4.dp)
            }
            if (!inPip) ctl.message?.let { m ->
                if (ctl.failed) {
                    Surface(
                        Modifier.align(Alignment.Center).fillMaxWidth(0.6f), shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = .85f), contentColor = Color.White
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides Lang.direction) {
                            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(m, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HmsButton(tr("إعادة المحاولة", "Retry")) { ctl.retryNow() }
                                    HmsButton(tr("رجوع", "Back"), primary = false) { ctl.close() }
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
            "audio" -> TrackDialog(ctl.player, C.TRACK_TYPE_AUDIO, tr("الصوت", "Audio"), false) { dialog = null }
            "text" -> TrackDialog(ctl.player, C.TRACK_TYPE_TEXT, tr("الترجمة", "Subtitles"), true) { dialog = null }
            "speed" -> SpeedDialog(ctl.speed, { ctl.changeSpeed(it); dialog = null }) { dialog = null }
        }
    }
}

private fun trackLabel(f: Format, n: Int): String {
    val lang = f.language?.takeIf { it != "und" }
        ?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale(if (Lang.isAr) "ar" else "en")) }
    val ch = f.channelCount.takeIf { it > 0 }?.let { "${it}ch" }
    return listOfNotNull(f.label, lang, ch).joinToString(" • ").ifBlank { tr("مسار $n", "Track $n") }
}

@Composable
private fun TrackDialog(player: Player, type: Int, title: String, allowOff: Boolean, onDismiss: () -> Unit) {
    val groups = remember(player) { player.currentTracks.groups.filter { it.type == type && it.isSupported } }
    val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(type)
    DialogShell(title, onDismiss, 0.45f) {
        if (groups.isEmpty() && !allowOff) {
            Text(tr("مفيش مسارات متاحة", "No tracks available"), color = TextMuted, fontSize = 14.sp)
        } else {
            LazyColumn(Modifier.heightIn(max = 230.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (allowOff) item {
                    OptionRow(tr("إيقاف", "Off"), disabled || groups.none { g -> (0 until g.length).any { g.isTrackSelected(it) } }) {
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
    DialogShell(tr("سرعة التشغيل", "Playback speed"), onDismiss, 0.35f) {
        LazyColumn(Modifier.heightIn(max = 230.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)) { s ->
                OptionRow(if (s == 1f) tr("عادية (1x)", "Normal (1x)") else "${s}x", s == current) { onPick(s) }
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

// ---------------------------------------------------------------------------------------------
// Picture-in-picture helpers
// ---------------------------------------------------------------------------------------------

private const val PIP_ACTION = "com.hms.player.PIP_TOGGLE"

private fun pipSupported(ctx: Context): Boolean =
    Build.VERSION.SDK_INT >= 26 && ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

/** PiP window shape (video ratio, limited to what Android allows) + a play/pause button. */
private fun pipParams(ctx: Context, ratio: Float, playing: Boolean, auto: Boolean): PictureInPictureParams? {
    if (Build.VERSION.SDK_INT < 26) return null
    val r = ratio.coerceIn(0.45f, 2.35f)
    val b = PictureInPictureParams.Builder().setAspectRatio(Rational((r * 1000).toInt(), 1000))
    val pi = PendingIntent.getBroadcast(
        ctx, 0, Intent(PIP_ACTION).setPackage(ctx.packageName),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val label = if (playing) tr("إيقاف", "Pause") else tr("تشغيل", "Play")
    val icon = AndroidIcon.createWithResource(ctx, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    b.setActions(listOf(RemoteAction(icon, label, label, pi)))
    if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(auto && playing)
    return b.build()
}

private fun enterPip(act: Activity?, ctx: Context, ratio: Float, playing: Boolean, auto: Boolean) {
    val p = pipParams(ctx, ratio, playing, auto) ?: return
    runCatching { act?.enterPictureInPictureMode(p) }
}
