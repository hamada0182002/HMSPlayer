package com.hms.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.ManageAccounts
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
import androidx.compose.ui.platform.LocalContext
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
    val clock by produceState("") {
        while (true) {
            value = SimpleDateFormat("hh:mm a   yyyy/MM/dd", Locale.getDefault()).format(Date())
            delay(20_000)
        }
    }
    val inf = vm.info
    val sub = buildString {
        inf?.expiry?.let { append("ينتهي الاشتراك: ").append(SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(it * 1000))) }
        if (inf != null && inf.maxConn.isNotBlank()) { if (isNotEmpty()) append("   •   "); append("الأجهزة: ${inf.activeConn.ifBlank { "0" }}/${inf.maxConn}") }
    }
    val cont = vm.continueList().size

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.logo_mark), null, Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("HMS Player", style = TextStyle(brush = BrandBrush, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold))
                Text(acc?.name ?: "", color = TextMuted, fontSize = 13.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(clock, color = TextMain, fontSize = 15.sp)
                if (sub.isNotEmpty()) Text(sub, color = TextMuted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.weight(0.64f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StaggerIn(0, Modifier.weight(1f).fillMaxHeight()) {
                Tile("مباشر", "قنوات البث المباشر", Icons.Default.LiveTv, listOf(Color(0xFFFF9A1F), Color(0xFFE8362B)), true) { vm.push(Screen.Browse(Section.LIVE)) }
            }
            StaggerIn(1, Modifier.weight(1f).fillMaxHeight()) {
                Tile("أفلام", "مكتبة الأفلام", Icons.Default.Movie, listOf(Color(0xFF2A7BE8), Color(0xFF1B3FA8)), true) { vm.push(Screen.Browse(Section.VOD)) }
            }
            StaggerIn(2, Modifier.weight(1f).fillMaxHeight()) {
                Tile("مسلسلات", "كل المواسم والحلقات", Icons.Default.VideoLibrary, listOf(Color(0xFF8B3CF0), Color(0xFF4B2BC4)), true) { vm.push(Screen.Browse(Section.SERIES)) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.weight(0.36f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StaggerIn(3, Modifier.weight(1f).fillMaxHeight()) {
                Tile("متابعة المشاهدة", if (cont > 0) "$cont عنصر" else null, Icons.Default.History, listOf(Color(0xFF1FB58A), Color(0xFF127A66)), false) { vm.push(Screen.Continue) }
            }
            StaggerIn(4, Modifier.weight(1f).fillMaxHeight()) {
                Tile("الحسابات", "${vm.accounts.size} حساب", Icons.Default.ManageAccounts, listOf(Color(0xFF26324D), Color(0xFF18223A)), false) { vm.push(Screen.Accounts) }
            }
            StaggerIn(5, Modifier.weight(1f).fillMaxHeight()) {
                Tile("الإعدادات", "v" + Updater.currentVersion(LocalContext.current), Icons.Default.Settings, listOf(Color(0xFF3A3F4F), Color(0xFF232733)), false) { vm.push(Screen.Settings) }
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
            if (sub != null) Text(sub, color = Color.White.copy(alpha = .8f), fontSize = if (big) 13.sp else 12.sp)
        }
    }
}
