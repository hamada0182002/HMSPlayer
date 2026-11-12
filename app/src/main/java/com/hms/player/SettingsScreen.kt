package com.hms.player

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PowerSettingsNew
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
import kotlinx.coroutines.delay
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
    var auto by remember { mutableStateOf(vm.store.autoStart) }
    var autoPip by remember { mutableStateOf(vm.store.autoPip) }
    var autoHint by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    // when a result message appears (e.g. "you are on the latest version"), scroll down so it is visible
    LaunchedEffect(msg, autoHint) {
        if (msg != null || autoHint != null) { delay(120); scroll.animateScrollTo(scroll.maxValue) }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(tr("الإعدادات", "Settings"), onBack = { vm.pop() })
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingCard(tr("الحسابات", "Accounts")) {
                val curName = vm.active?.name ?: "—"
                Text(
                    tr("الحساب الحالي: $curName   •   عدد الحسابات: ${vm.accounts.size}", "Current account: $curName   •   Accounts: ${vm.accounts.size}"),
                    color = TextMain, fontSize = 14.sp
                )
                HmsButton(tr("إدارة الحسابات", "Manage accounts"), icon = Icons.Default.ManageAccounts) { vm.push(Screen.Accounts) }
            }

            SettingCard(tr("اللغة", "Language")) {
                Text(tr("اختار لغة التطبيق.", "Choose the app language."), color = TextMuted, fontSize = 13.sp, lineHeight = 20.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // language names are always written in their own language
                    HmsButton("العربية", icon = Icons.Default.Language, primary = Lang.current == AppLanguage.AR) { vm.changeLanguage(AppLanguage.AR) }
                    HmsButton("English", icon = Icons.Default.Language, primary = Lang.current == AppLanguage.EN) { vm.changeLanguage(AppLanguage.EN) }
                }
            }

            SettingCard(tr("نوع الجهاز", "Device type")) {
                Text(
                    tr("موبايل: التطبيق بالطول، والتشغيل بالعرض.\nتلفزيون: كل حاجة بالعرض.",
                        "Phone: the app is portrait, playback is landscape.\nTV: everything is landscape."),
                    color = TextMuted, fontSize = 13.sp, lineHeight = 20.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HmsButton(tr("موبايل", "Phone"), icon = Icons.Default.Smartphone, primary = vm.deviceMode == DeviceMode.PHONE) { vm.changeMode(DeviceMode.PHONE) }
                    HmsButton(tr("تلفزيون", "TV"), icon = Icons.Default.Tv, primary = vm.deviceMode == DeviceMode.TV) { vm.changeMode(DeviceMode.TV) }
                }
            }

            if (vm.deviceMode == DeviceMode.PHONE && Build.VERSION.SDK_INT >= 26) SettingCard(tr("صورة داخل صورة", "Picture in Picture")) {
                Text(
                    tr("وإنت بتشاهد، لو خرجت من التطبيق (زرار الهوم) الفيديو يكمّل في نافذة صغيرة فوق التطبيقات التانية. وتقدر تفتحها بإيدك من زرار صورة داخل صورة جوه المشغّل.",
                        "While watching, if you leave the app (Home button) the video keeps playing in a small window over other apps. You can also open it manually from the Picture in Picture button inside the player."),
                    color = TextMuted, fontSize = 13.sp, lineHeight = 20.sp
                )
                HmsButton(
                    if (autoPip) tr("الدخول التلقائي: مفعّل ✓", "Auto-enter: On ✓") else tr("الدخول التلقائي: متوقف", "Auto-enter: Off"),
                    icon = Icons.Default.PictureInPictureAlt, primary = autoPip
                ) {
                    autoPip = !autoPip
                    vm.store.autoPip = autoPip
                }
            }

            if (vm.deviceMode == DeviceMode.TV) SettingCard(tr("التشغيل التلقائي", "Auto-start")) {
                Text(
                    tr("يفتح البرنامج لوحده أول ما التلفزيون يشتغل.", "Opens the app by itself when the TV turns on."),
                    color = TextMuted, fontSize = 13.sp, lineHeight = 20.sp
                )
                HmsButton(
                    if (auto) tr("التشغيل التلقائي: مفعّل ✓", "Auto-start: On ✓") else tr("التشغيل التلقائي: متوقف", "Auto-start: Off"),
                    icon = Icons.Default.PowerSettingsNew, primary = auto
                ) {
                    auto = !auto
                    vm.store.autoStart = auto
                    autoHint = null
                    if (auto && Build.VERSION.SDK_INT >= 29 && !Settings.canDrawOverlays(ctx)) {
                        // Android 10+ only lets an app open itself at boot if "display over other apps" is allowed
                        autoHint = tr("اسمح للتطبيق بـ \"العرض فوق التطبيقات الأخرى\" علشان يفتح لوحده.", "Allow the app to \"Display over other apps\" so it can open by itself.")
                        try {
                            ctx.startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) {
                            autoHint = tr("فعّل إذن \"العرض فوق التطبيقات الأخرى\" للتطبيق من إعدادات التلفزيون (Apps > HMS Player).", "Enable the \"Display over other apps\" permission for the app from the TV settings (Apps > HMS Player).")
                        }
                    }
                }
                autoHint?.let { Text(it, color = OrangeSoft, fontSize = 13.sp, lineHeight = 20.sp) }
            }

            SettingCard(tr("التحديث", "Update")) {
                Text(tr("الإصدار الحالي: ${Updater.currentVersion(ctx)}", "Current version: ${Updater.currentVersion(ctx)}"), color = TextMain, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (checking) CircularProgressIndicator(Modifier.size(28.dp), color = Orange, strokeWidth = 3.dp)
                    else HmsButton(tr("التحقق من التحديث", "Check for updates"), icon = Icons.Default.SystemUpdate) {
                        checking = true; msg = null
                        scope.launch {
                            try {
                                val u = Updater.check(ctx)
                                if (u == null) msg = tr("إنت على آخر إصدار ✓", "You are on the latest version ✓") else found = u
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

            Spacer(Modifier.height(120.dp)) // extra room at the bottom so nothing is cut off on TV
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

    DialogShell(tr("إصدار جديد ${info.version} متاح", "New version ${info.version} available"), { if (progress == null) onDismiss() }, 0.6f) {
        if (info.notes.isNotBlank()) {
            Column(Modifier.heightIn(max = 120.dp).verticalScroll(rememberScrollState())) {
                Text(info.notes, color = TextMuted, fontSize = 13.sp, lineHeight = 20.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
        if (info.size > 0) Text(tr("الحجم", "Size") + ": %.1f MB".format(java.util.Locale.US, info.size / 1048576f), color = TextMuted, fontSize = 13.sp)
        progress?.let { p ->
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth().height(6.dp), color = Orange, trackColor = Surface2)
            Text(tr("جاري التحميل... ${(p * 100).toInt()}%", "Downloading... ${(p * 100).toInt()}%"), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        if (needPerm) Text(tr("اسمح لـ HMS Player بتثبيت التطبيقات من الإعدادات اللي اتفتحت، وارجع واضغط \"تثبيت\".", "Allow HMS Player to install apps from the settings that just opened, then come back and tap \"Install\"."), color = OrangeSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        err?.let { Text(it, color = Danger, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (progress == null) {
                val f = file
                if (f != null) HmsButton(tr("تثبيت", "Install"), icon = Icons.Default.SystemUpdate) { install(f) }
                else HmsButton(tr("تحديث الآن", "Update now"), icon = Icons.Default.SystemUpdate) { start() }
                HmsButton(tr("لاحقاً", "Later"), primary = false, onClick = onDismiss)
            }
        }
    }
}

@Composable
fun BusyDialog(msg: String, onRetry: () -> Unit, onForce: () -> Unit, onCancel: () -> Unit) {
    DialogShell(tr("الحساب مشغول", "Account busy"), onCancel, 0.6f) {
        Text(msg, color = TextMuted, fontSize = 14.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HmsButton(tr("إعادة المحاولة", "Retry"), icon = Icons.Default.Replay) { onRetry() }
            HmsButton(tr("شغّل رغم كده", "Play anyway"), primary = false, onClick = onForce)
            HmsButton(tr("إلغاء", "Cancel"), primary = false, onClick = onCancel)
        }
    }
}