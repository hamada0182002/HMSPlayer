package com.hms.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun AccountsScreen(vm: AppVM) {
    var adding by remember { mutableStateOf(vm.accounts.isEmpty()) }
    var editing by remember { mutableStateOf<Account?>(null) }
    var deleting by remember { mutableStateOf<Account?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopBar(tr("الحسابات", "Accounts"), onBack = if (vm.stack.size > 1) ({ vm.pop() }) else null) {
            HmsButton(tr("إضافة حساب", "Add account"), icon = Icons.Default.Add) { adding = true }
        }
        if (vm.accounts.isEmpty()) {
            EmptyBox(tr("مفيش حسابات لسه.\nاضغط \"إضافة حساب\" وادخل بيانات اشتراك Xtream.", "No accounts yet.\nTap \"Add account\" and enter your Xtream subscription details."))
        } else {
            LazyVerticalGrid(
                GridCells.Adaptive(300.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(vm.accounts, key = { it.id }) { a ->
                    AccountCard(
                        a, isActive = vm.active?.id == a.id,
                        onUse = { vm.select(a) }, onEdit = { editing = a }, onDelete = { deleting = a }
                    )
                }
            }
        }
    }

    if (adding) AccountDialog(vm, null) { adding = false }
    editing?.let { e -> AccountDialog(vm, e) { editing = null } }
    deleting?.let { d ->
        DialogShell(tr("حذف الحساب", "Delete account"), { deleting = null }, 0.4f) {
            Text(tr("متأكد إنك عايز تحذف \"${d.name}\"؟", "Are you sure you want to delete \"${d.name}\"?"), fontSize = 15.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HmsButton(tr("حذف", "Delete"), icon = Icons.Default.Delete) { vm.deleteAccount(d.id); deleting = null }
                HmsButton(tr("إلغاء", "Cancel"), primary = false) { deleting = null }
            }
        }
    }
}

@Composable
private fun AccountCard(a: Account, isActive: Boolean, onUse: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp), color = Surface1,
        border = if (isActive) androidx.compose.foundation.BorderStroke(2.dp, Orange) else null
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (isActive) {
                    Surface(shape = RoundedCornerShape(50), color = Orange.copy(alpha = .2f)) {
                        Text(tr("الحالي", "Current"), color = OrangeSoft, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(a.server.removePrefix("http://").removePrefix("https://"), color = TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tr("المستخدم: ${a.user}", "User: ${a.user}"), color = TextMuted, fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HmsButton(if (isActive) tr("فتح", "Open") else tr("دخول", "Sign in"), icon = Icons.Default.Login, modifier = Modifier.weight(1f), onClick = onUse)
                IconBtn(Icons.Default.Edit, tr("تعديل", "Edit"), onClick = onEdit)
                IconBtn(Icons.Default.Delete, tr("حذف", "Delete"), tint = Danger, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun AccountDialog(vm: AppVM, existing: Account?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var server by remember { mutableStateOf(existing?.server ?: "") }
    var user by remember { mutableStateOf(existing?.user ?: "") }
    var pass by remember { mutableStateOf(existing?.pass ?: "") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun build() = Api.buildAccount(existing?.id, name, server, user, pass)
    fun commit(a: Account) { vm.saveAccount(a, activate = existing == null); onDismiss() }
    fun save() {
        if (server.isBlank()) { err = tr("ادخل رابط السيرفر", "Enter the server URL"); return }
        val a = build()
        if (a.user.isBlank() || a.pass.isBlank()) { err = tr("ادخل اليوزر والباسورد", "Enter the username and password"); return }
        busy = true; err = null
        scope.launch {
            try { Api.info(a); commit(a) } catch (e: Exception) { err = friendly(e); busy = false }
        }
    }

    val ltr = TextStyle(fontSize = 14.sp, color = TextMain, textDirection = TextDirection.Ltr, textAlign = TextAlign.Start)
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Orange, unfocusedBorderColor = Color.White.copy(alpha = .25f),
        focusedLabelColor = OrangeSoft, cursorColor = Orange
    )

    val compact = isCompact()
    DialogShell(if (existing == null) tr("إضافة حساب Xtream", "Add Xtream account") else tr("تعديل الحساب", "Edit account"), { if (!busy) onDismiss() }, 0.78f) {
        val fName: @Composable (Modifier) -> Unit = { m ->
            OutlinedTextField(name, { name = it }, m, label = { Text(tr("اسم الحساب (اختياري)", "Account name (optional)")) }, singleLine = true, textStyle = ltr, colors = colors)
        }
        val fServer: @Composable (Modifier) -> Unit = { m ->
            OutlinedTextField(
                server, { server = it }, m, label = { Text(tr("رابط السيرفر", "Server URL") + "  http://example.com:8080") },
                singleLine = true, textStyle = ltr, colors = colors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
        }
        val fUser: @Composable (Modifier) -> Unit = { m ->
            OutlinedTextField(user, { user = it }, m, label = { Text(tr("اليوزر", "Username")) }, singleLine = true, textStyle = ltr, colors = colors)
        }
        val fPass: @Composable (Modifier) -> Unit = { m ->
            OutlinedTextField(pass, { pass = it }, m, label = { Text(tr("الباسورد", "Password")) }, singleLine = true, textStyle = ltr, colors = colors)
        }
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fName(Modifier.fillMaxWidth()); fServer(Modifier.fillMaxWidth())
                fUser(Modifier.fillMaxWidth()); fPass(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { fName(Modifier.weight(1f)); fServer(Modifier.weight(1.4f)) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { fUser(Modifier.weight(1f)); fPass(Modifier.weight(1f)) }
        }
        err?.let { Text(it, color = Danger, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (busy) CircularProgressIndicator(Modifier.padding(horizontal = 12.dp), color = Orange, strokeWidth = 3.dp)
            else HmsButton(tr("حفظ", "Save"), onClick = ::save)
            HmsButton(tr("إلغاء", "Cancel"), primary = false) { if (!busy) onDismiss() }
            if (err != null && !busy && server.isNotBlank()) HmsButton(tr("حفظ بدون فحص", "Save without checking"), primary = false) { commit(build()) }
        }
    }
}
