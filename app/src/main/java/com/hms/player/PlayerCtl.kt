@file:OptIn(UnstableApi::class)

package com.hms.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * All player logic: ExoPlayer, auto-reconnect (live + movies), resume position,
 * next/previous (channels / episodes), progress saving.
 */
class PlayerCtl(
    ctx: Context,
    private val vm: AppVM,
    val req: PlayerRequest,
    private val scope: CoroutineScope
) {
    val player: ExoPlayer = ExoPlayer.Builder(
        ctx,
        DefaultRenderersFactory(ctx)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
    )
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(ctx).setDataSourceFactory(
                DefaultHttpDataSource.Factory()
                    .setUserAgent(IPTV_UA)
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15_000)
                    .setReadTimeoutMs(20_000)
            )
        )
        .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(20_000, 60_000, 2_000, 5_000).build())
        .setHandleAudioBecomingNoisy(true)
        .build()

    var index by mutableIntStateOf(req.index)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(true)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    var toast by mutableStateOf<String?>(null)
        private set
    var pos by mutableLongStateOf(0L)
        private set
    var dur by mutableLongStateOf(0L)
        private set
    var videoSize by mutableStateOf("")
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var resize by mutableIntStateOf(vm.store.resizeMode)
        private set

    val cur: PlayItem get() = req.items[index]
    val hasPrev get() = index > 0
    val hasNext get() = index < req.items.size - 1

    private var retry = 0
    private var everPlayed = false
    private var job: Job? = null
    private var toastJob: Job? = null
    private var lastSave = 0L
    private var stableSince = 0L

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) { message = null; failed = false }
                if (state == Player.STATE_ENDED) ended()
            }

            override fun onIsPlayingChanged(p: Boolean) {
                isPlaying = p
                if (p) { everPlayed = true; stableSince = System.currentTimeMillis() }
            }

            override fun onPlayerError(error: PlaybackException) = reconnect(error)

            override fun onVideoSizeChanged(v: VideoSize) {
                videoSize = if (v.width > 0) "${v.width}×${v.height}" else ""
            }
        })
    }

    fun start() = play(req.index, req.fromStart)

    /** Plays item [i]. VOD resumes from the saved position unless [fromStart]. */
    fun play(i: Int, fromStart: Boolean = false) {
        job?.cancel()
        saveNow()
        index = i
        retry = 0; everPlayed = false; failed = false; message = null; pos = 0; dur = 0
        var startMs = C.TIME_UNSET
        val it = cur
        if (!it.live && !fromStart) {
            vm.progressOf(it.key)?.let { p -> if (!p.done && p.pos > 15_000) startMs = (p.pos - 3_000).coerceAtLeast(0) }
        }
        load(it, startMs)
    }

    private fun load(it: PlayItem, startMs: Long) {
        player.setMediaItem(MediaItem.fromUri(it.url), startMs)
        player.prepare()
        player.playWhenReady = true
    }

    fun retryNow() {
        job?.cancel(); retry = 0; failed = false; message = null
        load(cur, if (cur.live || pos < 5_000) C.TIME_UNSET else pos - 3_000)
    }

    private fun reconnect(e: PlaybackException?) {
        if (job?.isActive == true) return
        val it = cur
        retry++
        val limit = if (!everPlayed && !it.live) 2 else 8
        if (retry > limit) {
            failed = true
            message = if (!everPlayed && !it.live)
                "الملف مش شغال (${e?.errorCodeName ?: "?"}). غالباً عدد الأجهزة المسموح بيها في اشتراكك اتملى، أو الملف معطّل، أو صيغته مش مدعومة."
            else "تعذّر تشغيل البث (${e?.errorCodeName ?: "?"}). اتأكد من الإنترنت واضغط إعادة المحاولة."
            return
        }
        message = "انقطع البث... بعيد الاتصال تلقائياً (محاولة $retry)"
        val resume = if (it.live || pos < 5_000) C.TIME_UNSET else pos - 3_000
        job = scope.launch {
            delay(minOf(1_500L * retry, 15_000L))
            load(it, resume)
        }
    }

    private fun ended() {
        val it = cur
        if (it.live) { reconnect(null); return }
        val d = player.duration
        if (d != C.TIME_UNSET && player.currentPosition < d - 30_000 && retry < 3) { reconnect(null); return }
        markDone()
        if (hasNext && !req.items[index + 1].live) play(index + 1) else close()
    }

    private fun markDone() {
        val it = cur
        vm.saveProgress(it, dur, dur, true)
        // so the next episode shows up in "continue watching"
        req.items.getOrNull(index + 1)?.let { n ->
            if (!n.live && n.sid != null && vm.progressOf(n.key) == null) vm.saveProgress(n, 0, 0, false)
        }
    }

    fun saveNow() {
        val it = cur
        if (it.live || dur <= 0) return
        if (pos > (dur * 0.95).toLong()) markDone() else if (pos > 10_000) vm.saveProgress(it, pos, dur, false)
    }

    /** called ~2x per second from the UI */
    fun tick() {
        pos = player.currentPosition.coerceAtLeast(0)
        val d = player.duration
        dur = if (d == C.TIME_UNSET || d < 0) 0 else d
        val now = System.currentTimeMillis()
        if (isPlaying && now - stableSince > 20_000) retry = 0
        if (!cur.live && now - lastSave > 5_000) { lastSave = now; saveNow() }
    }

    fun next() { if (hasNext) play(index + 1) }
    fun prev() { if (hasPrev) play(index - 1) }

    /**
     * App went to background / screen off: really close the HTTP connection.
     * (A paused player keeps its socket open, so the server keeps counting this device.)
     */
    fun suspendStream() {
        job?.cancel()
        saveNow()
        player.playWhenReady = false
        player.stop()
        message = null
    }

    fun playNow() {
        if (player.playbackState == Player.STATE_IDLE) { retry = 0; failed = false; player.prepare() }
        player.play()
    }

    fun toggle() {
        if (player.isPlaying) player.pause()
        else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    fun seekBy(ms: Long) {
        val d = player.duration
        val max = if (d == C.TIME_UNSET || d <= 0) Long.MAX_VALUE else d
        player.seekTo((player.currentPosition + ms).coerceIn(0L, max))
    }

    fun seekTo(ms: Long) = player.seekTo(ms.coerceAtLeast(0))

    fun changeSpeed(s: Float) { speed = s; player.setPlaybackSpeed(s); flash("السرعة ${s}x") }

    fun cycleResize() {
        resize = when (resize) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        vm.store.resizeMode = resize
        flash(when (resize) {
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "ملء الشاشة (تمديد)"
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "تكبير (قص الأطراف)"
            else -> "الأصلي (ملائم)"
        })
    }

    fun flash(t: String) {
        toast = t
        toastJob?.cancel()
        toastJob = scope.launch { delay(1_100); toast = null }
    }

    fun close() {
        tick()
        vm.closePlayer()
    }

    fun release() {
        job?.cancel(); toastJob?.cancel()
        saveNow()
        player.release()
    }
}
