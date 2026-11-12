package com.hms.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val RECENT = "__recent__"

@Composable
fun BrowseScreen(vm: AppVM, section: Section) {
    val acc = vm.active ?: return
    val compact = isCompact()
    var retry by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchAll by remember { mutableStateOf(false) }
    val cats = produceLoad(Pair(acc.id, section), retry) { vm.categories(section) }

    Column(Modifier.fillMaxSize()) {
        TopBar(if (searching && compact) "" else section.title, onBack = { vm.pop() }) {
            if (searching) {
                SearchField(query, { query = it; searchAll = false }, Modifier.width(if (compact) 200.dp else 260.dp))
                Spacer(Modifier.width(8.dp))
                IconBtn(Icons.Default.Close, "إغلاق البحث") { searching = false; query = ""; searchAll = false }
            } else {
                IconBtn(Icons.Default.Search, "بحث") { searching = true }
            }
        }
        when (val c = cats) {
            is Load.Loading -> LoadingBox()
            is Load.Err -> ErrorBox(c.msg) { retry++ }
            is Load.Ok -> {
                val base = c.v.ifEmpty { listOf(Category("", "الكل")) }
                // movies & series get a "recently added" list at the top
                val list = if (section == Section.LIVE) base else listOf(Category(RECENT, "🆕 مضاف حديثاً")) + base
                BrowseBody(vm, section, list, query, searchAll, compact) { searchAll = true }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(color = TextMain, fontSize = 15.sp),
        cursorBrush = SolidColor(Orange),
        modifier = modifier.height(42.dp).focusRequester(fr)
            .background(Surface2, RoundedCornerShape(21.dp)).padding(horizontal = 16.dp),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text("ابحث بالاسم...", color = TextMuted, fontSize = 15.sp)
                inner()
            }
        }
    )
}

@Composable
private fun BrowseBody(
    vm: AppVM, section: Section, cats: List<Category>,
    query: String, searchAll: Boolean, compact: Boolean, onSearchAll: () -> Unit
) {
    val acc = vm.active ?: return
    val tv = LocalTv.current
    val scope = rememberCoroutineScope()
    // start on the first real category (not the heavy "recent" one), or where the user was last time
    val startIdx = remember(section) { if (cats.first().id == RECENT && cats.size > 1) 1 else 0 }
    var sel by remember(section) { mutableIntStateOf((vm.catSel[section] ?: startIdx).coerceIn(cats.indices)) }
    val initSel = remember(section) { sel }
    val catFr = remember(section) { FocusRequester() }     // the selected category (first focus)
    val firstFr = remember(section) { FocusRequester() }   // the first channel / movie in the grid
    val enterJob = remember(section) { arrayOfNulls<Job>(1) }
    var retry by remember { mutableIntStateOf(0) }
    val cat = cats[sel.coerceIn(cats.indices)]
    val isRecent = cat.id == RECENT
    val q = norm(query.trim())
    val allMode = isRecent || (searchAll && q.isNotEmpty())
    val catId: String? = if (allMode) null else cat.id.ifEmpty { null }
    val load = produceLoad(Triple(acc.id, section, catId), retry) { vm.items(section, catId) }
    val gs: LazyGridState = if (q.isEmpty()) vm.gridState("$section|${cat.id}") else rememberLazyGridState()

    LaunchedEffect(sel) { vm.catSel[section] = sel }
    // TV: the remote starts on the categories list (up / down = change category)
    LaunchedEffect(section) { if (tv && !compact) { delay(150); runCatching { catFr.requestFocus() } } }

    val pane: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            ItemsPane(vm, section, cat.id, isRecent, q, searchAll, allMode, load, gs, firstFr, { retry++ }, onSearchAll)
        }
    }

    if (compact) {
        Column(Modifier.fillMaxSize()) {
            LazyRow(
                Modifier.fillMaxWidth().height(50.dp), state = vm.listState("row|$section"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(cats, key = { i, c -> "${i}_${c.id}" }) { i, c ->
                    val on = i == sel
                    FocusBox(Modifier.height(38.dp), RoundedCornerShape(19.dp), { sel = i }) {
                        Box(Modifier.matchParentSize().background(if (on) Orange.copy(alpha = .28f) else Surface2))
                        Text(
                            c.name, color = if (on) OrangeSoft else TextMain, fontSize = 14.sp, maxLines = 1,
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp)
                        )
                    }
                }
            }
            pane(Modifier.weight(1f).fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.width(190.dp).fillMaxHeight().onPreviewKeyEvent { ev ->
                    // TV: "left" from the categories enters the FIRST channel / movie
                    if (tv && ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft) {
                        if (ev.nativeKeyEvent.repeatCount == 0) {
                            enterJob[0]?.cancel()
                            enterJob[0] = scope.launch {
                                runCatching { gs.scrollToItem(0) }
                                repeat(40) {                       // the list may still be loading: retry ~4s
                                    delay(100)
                                    if (runCatching { firstFr.requestFocus() }.isSuccess) return@launch
                                }
                            }
                        }
                        true
                    } else false
                },
                state = vm.listState("cat|$section"),
                contentPadding = PaddingValues(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(cats, key = { i, c -> "${i}_${c.id}" }) { i, c ->
                    val on = i == sel
                    FocusBox(
                        Modifier.fillMaxWidth().height(44.dp), RoundedCornerShape(12.dp),
                        { sel = i },   // a category opens only when you press OK
                        focusRequester = if (i == initSel) catFr else null
                    ) {
                        Box(Modifier.fillMaxSize().background(if (on) Orange.copy(alpha = .22f) else Color.Transparent))
                        Text(
                            c.name, color = if (on) OrangeSoft else TextMain, fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp)
                        )
                    }
                }
            }
            pane(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun ItemsPane(
    vm: AppVM, section: Section, catKey: String, isRecent: Boolean, q: String,
    searchAll: Boolean, allMode: Boolean, load: Load<List<Item>>,
    gs: LazyGridState, firstFr: FocusRequester, onRetry: () -> Unit, onSearchAll: () -> Unit
) {
    when (load) {
        is Load.Loading -> LoadingBox(if (allMode) "جاري تحميل القسم كله...\n(ممكن ياخد وقت حسب حجم القائمة)" else "جاري التحميل...")
        is Load.Err -> ErrorBox(load.msg, onRetry)
        is Load.Ok -> {
            val all = load.v
            val recent = remember(all, isRecent) {
                if (!isRecent) all
                else all.filter { it.added > 0 }.sortedByDescending { it.added }.take(150)
                    .ifEmpty { all.takeLast(150).reversed() }
            }
            val pool = if (isRecent && q.isNotEmpty()) all else recent
            // normalise the names once per list (not on every keystroke)
            val names = remember(pool) { lazy { pool.map { norm(it.name) } } }
            val shown = remember(pool, q) {
                if (q.isEmpty()) pool else { val n = names.value; pool.filterIndexed { i, _ -> n[i].contains(q) } }
            }
            val isLive = section == Section.LIVE
            Column(Modifier.fillMaxSize()) {
                if (q.isNotEmpty() && !searchAll && !isRecent) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${shown.size} نتيجة في القسم الحالي", color = TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        HmsButton("ابحث في كل القسم", primary = false, onClick = onSearchAll)
                    }
                }
                if (shown.isEmpty()) {
                    EmptyBox(if (q.isEmpty()) "مفيش حاجة هنا" else "مفيش نتايج")
                } else {
                    LazyVerticalGrid(
                        GridCells.Adaptive(if (isLive) 140.dp else 112.dp),
                        state = gs,
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(shown, key = { _, m -> m.id }) { i, m ->
                            val pr = if (section == Section.VOD) vm.progressOf("m${m.id}") else null
                            val frac = pr?.let { p -> if (p.done) 1f else if (p.dur > 0) p.pos.toFloat() / p.dur else 0f }
                            Poster(
                                title = m.name, img = m.img,
                                aspect = if (isLive) 1.35f else 2f / 3f, fit = isLive,
                                progress = frac,
                                focusRequester = if (i == 0) firstFr else null
                            ) {
                                when (section) {
                                    Section.LIVE -> vm.openPlayer(shown.map { x -> vm.playItemFor(x) }, i)
                                    Section.VOD -> vm.push(Screen.Movie(m))
                                    Section.SERIES -> vm.push(Screen.Series(m))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
