package com.hms.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(vm: AppVM) {
    val acc = vm.active
    val compact = isCompact()
    val clock by produceState("") {
        while (true) {
            value = SimpleDateFormat("hh:mm a   yyyy/MM/dd", Locale.getDefault()).format(Date())
            delay(20_000)
        }
    }
    val inf = vm.info
    val sub = buildString {
        inf?.expiry?.let { append(tr("ينتهي الاشتراك: ", "Expires: ")).append(SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(it * 1000))) }
        if (inf != null && inf.maxConn.isNotBlank()) { if (isNotEmpty()) append("   •   "); append(tr("الأجهزة: ", "Devices: ") + "${inf.activeConn.ifBlank { "0" }}/${inf.maxConn}") }
    }
    val cont = vm.continueList().size

    val live: @Composable () -> Unit = {
        Tile(tr("مباشر", "Live"), tr("قنوات البث المباشر", "Live TV channels"), Icons.Default.LiveTv, listOf(Color(0xFFFF9A1F), Color(0xFFE8362B)), true) { vm.push(Screen.Browse(Section.LIVE)) }
    }
    val movies: @Composable () -> Unit = {
        Tile(tr("أفلام", "Movies"), tr("مكتبة الأفلام", "Movie library"), Icons.Default.Movie, listOf(Color(0xFF2A7BE8), Color(0xFF1B3FA8)), true) { vm.push(Screen.Browse(Section.VOD)) }
    }
    val series: @Composable () -> Unit = {
        Tile(tr("مسلسلات", "Series"), tr("كل المواسم والحلقات", "All seasons and episodes"), Icons.Default.VideoLibrary, listOf(Color(0xFF8B3CF0), Color(0xFF4B2BC4)), true) { vm.push(Screen.Browse(Section.SERIES)) }
    }
    val resume: @Composable () -> Unit = {
        Tile(tr("متابعة المشاهدة", "Continue watching"), if (cont > 0) tr("$cont عنصر", if (cont == 1) "1 item" else "$cont items") else null, Icons.Default.History, listOf(Color(0xFF1FB58A), Color(0xFF127A66)), false) { vm.push(Screen.Continue) }
    }
    val settings: @Composable () -> Unit = {
        Tile(tr("الإعدادات", "Settings"), tr("الحسابات • الجهاز • التحديث", "Accounts • Device • Update"), Icons.Default.Settings, listOf(Color(0xFF3A4660), Color(0xFF232C42)), false) { vm.push(Screen.Settings) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.logo_mark), null, Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("HMS Player", style = TextStyle(brush = BrandBrush, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold))
                Text(acc?.name ?: "", color = TextMuted, fontSize = 13.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(clock, color = TextMain, fontSize = if (compact) 13.sp else 15.sp)
                if (!compact && sub.isNotEmpty()) Text(sub, color = TextMuted, fontSize = 12.sp)
            }
        }
        if (compact && sub.isNotEmpty()) Text(sub, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(10.dp))

        if (compact) {
            // vertical phone: big tiles stacked, two small ones at the bottom
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StaggerIn(0, Modifier.weight(1f).fillMaxWidth()) { live() }
                StaggerIn(1, Modifier.weight(1f).fillMaxWidth()) { movies() }
                StaggerIn(2, Modifier.weight(1f).fillMaxWidth()) { series() }
                Row(Modifier.fillMaxWidth().height(92.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StaggerIn(3, Modifier.weight(1f).fillMaxHeight()) { resume() }
                    StaggerIn(4, Modifier.weight(1f).fillMaxHeight()) { settings() }
                }
            }
        } else {
            Row(Modifier.weight(0.64f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StaggerIn(0, Modifier.weight(1f).fillMaxHeight()) { live() }
                StaggerIn(1, Modifier.weight(1f).fillMaxHeight()) { movies() }
                StaggerIn(2, Modifier.weight(1f).fillMaxHeight()) { series() }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.weight(0.36f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StaggerIn(3, Modifier.weight(1f).fillMaxHeight()) { resume() }
                StaggerIn(4, Modifier.weight(1f).fillMaxHeight()) { settings() }
            }
        }
    }
}

@Composable
private fun Tile(title: String, sub: String?, icon: ImageVector, colors: List<Color>, big: Boolean, onClick: () -> Unit) {
    FocusBox(Modifier.fillMaxSize(), RoundedCornerShape(24.dp), onClick) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors)))
        Icon(
            icon, null, tint = Color.White.copy(alpha = .10f),
            modifier = Modifier.align(Alignment.BottomStart).size(if (big) 150.dp else 90.dp).padding(8.dp)
        )
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(if (big) 54.dp else 34.dp))
            Spacer(Modifier.height(if (big) 10.dp else 4.dp))
            Text(title, color = Color.White, fontSize = if (big) 22.sp else 17.sp, fontWeight = FontWeight.ExtraBold)
            if (sub != null) Text(sub, color = Color.White.copy(alpha = .8f), fontSize = if (big) 13.sp else 11.sp, maxLines = 1)
        }
    }
}
