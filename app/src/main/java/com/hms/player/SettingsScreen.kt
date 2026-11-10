package com.hms.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

@Composable
private fun SettingCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Surface1) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = OrangeSoft, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun SettingsScreen(vm: AppVM) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<UpdateInfo?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopBar("الإعدادات", onBack = { vm.pop() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingCard("الحسابات") {
                Text("الحساب الحالي: ${vm.active?.name ?: "—"}   •   عدد الحسابات: ${vm.accounts.size}", color = TextMain, fontSize = 14.sp)
                HmsButton("إدارة الحسابات", icon = Icons.Default.ManageAccounts) { vm.push(Screen.Accounts) }
            }

            SettingCard("نوع الجهاز") {
                Text(
                    "موبايل: التطبيق بالطول، والتشغيل بالعرض.\nتلفزيون: كل حاجة بالعرض.",
                    color = TextMuted, fontSize = 13.sp, lineHeight = 20.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HmsButton("موبايل", icon = Icons.Default.Smartphone, primary = vm.deviceMode == DeviceMode.PHONE) { vm.changeMode(DeviceMode.PHONE) }
                    HmsButton("تلفزيون", icon = Icons.Default.Tv, primary = vm.deviceMode == DeviceMode.TV) { vm.changeMode(DeviceMode.TV) }
                }
            }

            SettingCard("التحديث") {
                Text("الإصدار الحالي: ${Updater.currentVersion(ctx)}", color = TextMain, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (checking) CircularProgressIndicator(Modifier.size(28.dp), color = Orange, strokeWidth = 3.dp)
                    else HmsButton("التحقق من التحديث", icon = Icons.Default.SystemUpdate) {
                        checking = true; msg = null
                        scope.launch {
                            try {
                                val u = Updater.check(ctx)
                                if (u == null) msg = "إنت على آخر إصدار ✓" else found = u
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                msg = friendly(e)
                            }
                            checking = false
                        }
                    }
                }
                msg?.let { Text(it, color = if (it.contains("✓")) Ok else OrangeSoft, fontSize = 14.sp) }
            }
        }
    }
    found?.let { UpdateDialog(it) { found = null } }
}

/** Downloads the new APK with a progress bar, then opens Android's installer. */
@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var needPerm by remember { mutableStateOf(false) }

    fun install(f: File) { needPerm = !Updater.install(ctx, f) }
    fun start() {
        err = null; progress = 0f
        scope.launch {
            try {
                val f = Updater.download(ctx, info) { progress = it }
                file = f; progress = null; install(f)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                err = friendly(e); progress = null
            }
        }
    }

    DialogShell("إصدار جديد ${info.version} متاح", { if (progress == null) onDismiss() }, 0.6f) {
        if (info.notes.isNotBlank()) {
            Column(Modifier.heightIn(max = 120.dp).verticalScroll(rememberScrollState())) {
                Text(info.notes, color = TextMuted, fontSize = 13.sp, lineHeight = 20.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
        if (info.size > 0) Text("الحجم: %.1f MB".format(java.util.Locale.US, info.size / 1048576f), color = TextMuted, fontSize = 13.sp)
        progress?.let { p ->
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth().height(6.dp), color = Orange, trackColor = Surface2)
            Text("جاري التحميل... ${(p * 100).toInt()}%", fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        if (needPerm) Text("اسمح لـ HMS Player بتثبيت التطبيقات من الإعدادات اللي اتفتحت، وارجع واضغط \"تثبيت\".", color = OrangeSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        err?.let { Text(it, color = Danger, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (progress == null) {
                val f = file
                if (f != null) HmsButton("تثبيت", icon = Icons.Default.SystemUpdate) { install(f) }
                else HmsButton("تحديث الآن", icon = Icons.Default.SystemUpdate) { start() }
                HmsButton("لاحقاً", primary = false, onClick = onDismiss)
            }
        }
    }
}

@Composable
fun BusyDialog(msg: String, onRetry: () -> Unit, onForce: () -> Unit, onCancel: () -> Unit) {
    DialogShell("الحساب مشغول", onCancel, 0.6f) {
        Text(msg, color = TextMuted, fontSize = 14.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HmsButton("إعادة المحاولة", icon = Icons.Default.Replay) { onRetry() }
            HmsButton("شغّل رغم كده", primary = false, onClick = onForce)
            HmsButton("إلغاء", primary = false, onClick = onCancel)
        }
    }
}
