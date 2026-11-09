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
        TopBar("الحسابات", onBack = if (vm.stack.size > 1) ({ vm.pop() }) else null) {
            HmsButton("إضافة حساب", icon = Icons.Default.Add) { adding = true }
        }
        if (vm.accounts.isEmpty()) {
            EmptyBox("مفيش حسابات لسه.\nاضغط \"إضافة حساب\" وادخل بيانات اشتراك Xtream.")
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
        DialogShell("حذف الحساب", { deleting = null }, 0.4f) {
            Text("متأكد إنك عايز تحذف \"${d.name}\"؟", fontSize = 15.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HmsButton("حذف", icon = Icons.Default.Delete) { vm.deleteAccount(d.id); deleting = null }
                HmsButton("إلغاء", primary = false) { deleting = null }
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
                        Text("الحالي", color = OrangeSoft, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(a.server.removePrefix("http://").removePrefix("https://"), color = TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("المستخدم: ${a.user}", color = TextMuted, fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HmsButton(if (isActive) "فتح" else "دخول", icon = Icons.Default.Login, modifier = Modifier.weight(1f), onClick = onUse)
                IconBtn(Icons.Default.Edit, "تعديل", onClick = onEdit)
                IconBtn(Icons.Default.Delete, "حذف", tint = Danger, onClick = onDelete)
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
        if (server.isBlank()) { err = "ادخل رابط السيرفر"; return }
        val a = build()
        if (a.user.isBlank() || a.pass.isBlank()) { err = "ادخل اليوزر والباسورد"; return }
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

    DialogShell(if (existing == null) "إضافة حساب Xtream" else "تعديل الحساب", { if (!busy) onDismiss() }, 0.78f) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.weight(1f), label = { Text("اسم الحساب (اختياري)") }, singleLine = true, textStyle = ltr, colors = colors)
            OutlinedTextField(
                server, { server = it }, Modifier.weight(1.4f), label = { Text("رابط السيرفر  http://example.com:8080") },
                singleLine = true, textStyle = ltr, colors = colors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(user, { user = it }, Modifier.weight(1f), label = { Text("اليوزر") }, singleLine = true, textStyle = ltr, colors = colors)
            OutlinedTextField(pass, { pass = it }, Modifier.weight(1f), label = { Text("الباسورد") }, singleLine = true, textStyle = ltr, colors = colors)
        }
        err?.let { Text(it, color = Danger, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (busy) CircularProgressIndicator(Modifier.padding(horizontal = 12.dp), color = Orange, strokeWidth = 3.dp)
            else HmsButton("حفظ", onClick = ::save)
            HmsButton("إلغاء", primary = false) { if (!busy) onDismiss() }
            if (err != null && !busy && server.isNotBlank()) HmsButton("حفظ بدون فحص", primary = false) { commit(build()) }
        }
    }
}
