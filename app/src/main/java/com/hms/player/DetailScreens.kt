package com.hms.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
private fun Cover(img: String?, title: String, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(Surface2, Surface1))),
        contentAlignment = Alignment.Center
    ) {
        Text(title.take(1), color = TextMuted.copy(alpha = .6f), fontSize = 40.sp, fontWeight = FontWeight.Bold)
        if (!img.isNullOrBlank()) AsyncImage(img, title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun MovieScreen(vm: AppVM, item: Item) {
    val acc = vm.active ?: return
    val compact = isCompact()
    val load = produceLoad(item.id) { Api.vodInfo(acc, item.id) }
    val info = (load as? Load.Ok)?.v
    val play = vm.playItemFor(item)
    val pr = vm.progressOf(play.key)
    val resumable = pr != null && !pr.done && pr.pos > 15_000
    val tv = LocalTv.current
    val playFr = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (tv) { delay(250); runCatching { playFr.requestFocus() } } }

    val buttons: @Composable () -> Unit = {
        if (resumable) {
            HmsButton("كمّل من ${fmtTime(pr!!.pos)}", icon = Icons.Default.PlayArrow, focusRequester = playFr) { vm.openPlayer(listOf(play), 0) }
            HmsButton("من البداية", icon = Icons.Default.Replay, primary = false) { vm.openPlayer(listOf(play), 0, fromStart = true) }
        } else {
            HmsButton("تشغيل", icon = Icons.Default.PlayArrow, focusRequester = playFr) { vm.openPlayer(listOf(play), 0, fromStart = true) }
        }
    }
    val meta = info?.meta?.ifBlank { null } ?: item.rating?.takeIf { (it.toFloatOrNull() ?: 0f) > 0f }?.let { "التقييم: $it/10" }
    val plot = when (load) {
        is Load.Loading -> "جاري تحميل القصة..."
        else -> info?.plot?.ifBlank { null } ?: "مفيش وصف متاح"
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(item.name, onBack = { vm.pop() })
        if (compact) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Cover(info?.img ?: item.img, item.name, Modifier.width(150.dp).aspectRatio(2f / 3f))
                Spacer(Modifier.height(14.dp))
                Text(item.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                if (!meta.isNullOrBlank()) Text(meta, color = OrangeSoft, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { buttons() }
                Spacer(Modifier.height(16.dp))
                Text(plot, color = Color(0xFFD6DBE3), fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, bottom = 12.dp)) {
                Cover(info?.img ?: item.img, item.name, Modifier.fillMaxHeight(0.95f).aspectRatio(2f / 3f, matchHeightConstraintsFirst = true))
                Column(Modifier.weight(1f).fillMaxHeight().padding(start = 22.dp).verticalScroll(rememberScrollState())) {
                    Text(item.name, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    if (!meta.isNullOrBlank()) Text(meta, color = OrangeSoft, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { buttons() }
                    Spacer(Modifier.height(16.dp))
                    Text(plot, color = Color(0xFFD6DBE3), fontSize = 14.sp, lineHeight = 22.sp)
                }
            }
        }
    }
}

@Composable
fun SeriesScreen(vm: AppVM, item: Item) {
    val acc = vm.active ?: return
    var retry by remember { mutableIntStateOf(0) }
    val load = produceLoad(item.id, retry) { Api.seriesInfo(acc, item.id, item.img) }

    Column(Modifier.fillMaxSize()) {
        TopBar(item.name, onBack = { vm.pop() })
        when (val l = load) {
            is Load.Loading -> LoadingBox()
            is Load.Err -> ErrorBox(l.msg) { retry++ }
            is Load.Ok -> {
                val si = l.v
                if (si.seasons.isEmpty()) EmptyBox("مفيش حلقات للمسلسل ده") else SeriesBody(vm, item, si)
            }
        }
    }
}

@Composable
private fun EpisodeRow(vm: AppVM, e: Episode, cover: String?, playlist: List<PlayItem>) {
    val key = "e${e.id}"
    val pr = vm.progressOf(key)
    val frac = pr?.let { p -> if (p.done) 1f else if (p.dur > 0) p.pos.toFloat() / p.dur else 0f } ?: 0f
    FocusBox(Modifier.fillMaxWidth().height(76.dp), RoundedCornerShape(14.dp), {
        vm.openPlayer(playlist, playlist.indexOfFirst { it.key == key })
    }) {
        Box(Modifier.fillMaxSize().background(Surface1))
        Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(e.img ?: cover, "${e.num}", Modifier.fillMaxHeight().aspectRatio(16f / 9f))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${e.num} - ${e.title.ifBlank { "حلقة ${e.num}" }}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!e.duration.isNullOrBlank()) Text(e.duration, color = TextMuted, fontSize = 12.sp)
            }
            if (pr?.done == true) Icon(Icons.Default.CheckCircle, null, tint = Ok, modifier = Modifier.size(22.dp))
            else Icon(Icons.Default.PlayArrow, null, tint = OrangeSoft, modifier = Modifier.size(26.dp))
        }
        if (frac > 0f && frac < 1f) {
            LinearProgressIndicator(
                progress = { frac }, color = Orange, trackColor = Color.White.copy(alpha = .2f),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp)
            )
        }
    }
}

@Composable
private fun SeasonChips(keys: List<Int>, season: Int, onPick: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)) {
        items(keys) { k -> HmsButton("الموسم $k", primary = k == season) { onPick(k) } }
    }
}

@Composable
private fun SeriesBody(vm: AppVM, item: Item, si: SeriesInfo) {
    val compact = isCompact()
    val keys = remember(si) { si.seasons.keys.sorted() }
    var season by remember(si) { mutableIntStateOf(keys.first()) }
    val playlist = remember(si) { vm.episodePlaylist(si, item.id, item.name, si.cover ?: item.img) }
    val eps = si.seasons[season].orEmpty()
    val plot = si.plot.ifBlank { item.plot.orEmpty() }.ifBlank { "مفيش وصف متاح" }

    if (compact) {
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth()) {
                    Cover(si.cover ?: item.img, item.name, Modifier.width(110.dp).aspectRatio(2f / 3f))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        if (si.meta.isNotBlank()) Text(si.meta, color = OrangeSoft, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(plot, color = Color(0xFFD6DBE3), fontSize = 13.sp, lineHeight = 20.sp, maxLines = 7, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (keys.size > 1) item { SeasonChips(keys, season) { season = it } }
            items(eps, key = { it.id }) { e -> EpisodeRow(vm, e, si.cover, playlist) }
        }
    } else {
        Row(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, bottom = 10.dp)) {
            Column(Modifier.weight(0.36f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Cover(si.cover ?: item.img, item.name, Modifier.fillMaxWidth(0.55f).aspectRatio(2f / 3f))
                Spacer(Modifier.height(10.dp))
                if (si.meta.isNotBlank()) Text(si.meta, color = OrangeSoft, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(plot, color = Color(0xFFD6DBE3), fontSize = 13.sp, lineHeight = 20.sp)
            }
            Column(Modifier.weight(0.64f).fillMaxHeight().padding(start = 18.dp)) {
                if (keys.size > 1) { SeasonChips(keys, season) { season = it }; Spacer(Modifier.height(8.dp)) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)) {
                    items(eps, key = { it.id }) { e -> EpisodeRow(vm, e, si.cover, playlist) }
                }
            }
        }
    }
}

@Composable
fun ContinueScreen(vm: AppVM) {
    val list = vm.continueList()
    Column(Modifier.fillMaxSize()) {
        TopBar("متابعة المشاهدة", onBack = { vm.pop() }) {
            if (list.isNotEmpty()) HmsButton("مسح السجل", icon = Icons.Default.Delete, primary = false) { vm.clearProgress() }
        }
        if (list.isEmpty()) {
            EmptyBox("مفيش حاجة لسه.\nابدأ فيلم أو حلقة وهتظهر هنا.")
        } else {
            LazyVerticalGrid(
                GridCells.Adaptive(122.dp), contentPadding = PaddingValues(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(list, key = { it.key }) { p ->
                    val frac = if (p.dur > 0) p.pos.toFloat() / p.dur else 0f
                    val badge = if (p.sid != null) (if (p.dur == 0L) "التالية " else "") + p.title.substringAfter(" - ", "") else null
                    Poster(title = p.seriesName ?: p.title, img = p.img, progress = frac, badge = badge) { vm.resume(p) }
                }
            }
        }
    }
}
