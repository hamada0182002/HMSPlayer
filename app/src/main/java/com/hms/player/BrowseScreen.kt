package com.hms.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrowseScreen(vm: AppVM, section: Section) {
    val acc = vm.active ?: return
    var retry by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchAll by remember { mutableStateOf(false) }
    val cats = produceLoad(Pair(acc.id, section), retry) { vm.categories(section) }

    Column(Modifier.fillMaxSize()) {
        TopBar(section.title, onBack = { vm.pop() }) {
            if (searching) {
                SearchField(query, { query = it; searchAll = false })
                Spacer(Modifier.width(8.dp))
                IconBtn(Icons.Default.Close, "إغلاق البحث") { searching = false; query = ""; searchAll = false }
            } else {
                IconBtn(Icons.Default.Search, "بحث") { searching = true }
            }
        }
        when (val c = cats) {
            is Load.Loading -> LoadingBox()
            is Load.Err -> ErrorBox(c.msg) { retry++ }
            is Load.Ok -> BrowseBody(
                vm, section,
                c.v.ifEmpty { listOf(Category("", "الكل")) },
                query, searchAll, onSearchAll = { searchAll = true }
            )
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(color = TextMain, fontSize = 15.sp),
        cursorBrush = SolidColor(Orange),
        modifier = Modifier.width(260.dp).height(42.dp).focusRequester(fr)
            .background(Surface2, RoundedCornerShape(21.dp)).padding(horizontal = 16.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text("ابحث بالاسم...", color = TextMuted, fontSize = 15.sp)
                inner()
            }
        }
    )
}

@Composable
private fun BrowseBody(
    vm: AppVM, section: Section, cats: List<Category>,
    query: String, searchAll: Boolean, onSearchAll: () -> Unit
) {
    val acc = vm.active ?: return
    var sel by remember { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    val q = norm(query.trim())
    val catId: String? = if (searchAll && q.isNotEmpty()) null else cats[sel.coerceIn(cats.indices)].id.ifEmpty { null }
    val load = produceLoad(Triple(acc.id, section, catId), retry) { vm.items(section, catId) }

    Row(Modifier.fillMaxSize()) {
        // ---- categories sidebar ----
        val ls = vm.listState("cat|$section")
        LazyColumn(
            Modifier.width(190.dp).fillMaxHeight(), state = ls,
            contentPadding = PaddingValues(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(cats, key = { i, c -> "${i}_${c.id}" }) { i, c ->
                val on = i == sel
                FocusBox(Modifier.fillMaxWidth().height(44.dp), RoundedCornerShape(12.dp), { sel = i }) {
                    Box(Modifier.fillMaxSize().background(if (on) Orange.copy(alpha = .22f) else Color.Transparent))
                    Text(
                        c.name, color = if (on) OrangeSoft else TextMain, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp)
                    )
                }
            }
        }

        // ---- content ----
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (val l = load) {
                is Load.Loading -> LoadingBox(if (catId == null && q.isNotEmpty()) "جاري تحميل القسم كله...\n(ممكن ياخد وقت حسب حجم القائمة)" else "جاري التحميل...")
                is Load.Err -> ErrorBox(l.msg) { retry++ }
                is Load.Ok -> {
                    val shown = remember(l.v, q) { if (q.isEmpty()) l.v else l.v.filter { norm(it.name).contains(q) } }
                    val gs: LazyGridState =
                        if (q.isEmpty()) vm.gridState("$section|$catId") else rememberLazyGridState()
                    val isLive = section == Section.LIVE
                    Column(Modifier.fillMaxSize()) {
                        if (q.isNotEmpty() && !searchAll) {
                            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${shown.size} نتيجة في القسم الحالي", color = TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                HmsButton("ابحث في كل القسم", primary = false, onClick = onSearchAll)
                            }
                        }
                        if (shown.isEmpty()) {
                            EmptyBox(if (q.isEmpty()) "مفيش حاجة هنا" else "مفيش نتايج")
                        } else {
                            LazyVerticalGrid(
                                GridCells.Adaptive(if (isLive) 150.dp else 122.dp),
                                state = gs,
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(shown, key = { _, it -> it.id }) { i, it ->
                                    val pr = if (section == Section.VOD) vm.progressOf("m${it.id}") else null
                                    val frac = pr?.let { p -> if (p.done) 1f else if (p.dur > 0) p.pos.toFloat() / p.dur else 0f }
                                    Poster(
                                        title = it.name, img = it.img,
                                        aspect = if (isLive) 1.35f else 2f / 3f, fit = isLive,
                                        progress = frac
                                    ) {
                                        when (section) {
                                            Section.LIVE -> vm.openPlayer(shown.map { x -> vm.playItemFor(x) }, i)
                                            Section.VOD -> vm.push(Screen.Movie(it))
                                            Section.SERIES -> vm.push(Screen.Series(it))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
