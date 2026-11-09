package com.hms.player

import android.app.Application
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class AppVM(app: Application) : AndroidViewModel(app) {
    val store = Store(app)

    var accounts by mutableStateOf(store.accounts())
        private set
    var active by mutableStateOf<Account?>(null)
        private set
    var info by mutableStateOf<AccountInfo?>(null)
        private set
    var player by mutableStateOf<PlayerRequest?>(null)
        private set
    private var progressTick by mutableIntStateOf(0)
    var pendingUpdate by mutableStateOf<UpdateInfo?>(null)
        private set
    fun dismissUpdate() { pendingUpdate = null }

    val stack = mutableStateListOf<Screen>()
    var forward = true

    private val catCache = HashMap<String, List<Category>>()
    private val itemCache = LinkedHashMap<String, List<Item>>()
    private val gridStates = HashMap<String, LazyGridState>()
    private val listStates = HashMap<String, LazyListState>()

    init {
        // silent update check at launch: if a newer release exists, a dialog is shown
        viewModelScope.launch {
            try { pendingUpdate = Updater.check(app) } catch (e: Exception) { /* offline / not configured: ignore */ }
        }
        val a = accounts.firstOrNull { it.id == store.activeId } ?: accounts.firstOrNull()
        if (a != null) select(a) else stack.add(Screen.Accounts)
    }

    /* ---------- navigation ---------- */
    fun push(s: Screen) { forward = true; stack.add(s) }
    fun pop() { if (stack.size > 1) { forward = false; stack.removeAt(stack.lastIndex) } }

    /* ---------- accounts ---------- */
    fun select(a: Account) {
        active = a
        store.activeId = a.id
        info = null
        catCache.clear(); itemCache.clear(); gridStates.clear(); listStates.clear()
        forward = true
        stack.clear(); stack.add(Screen.Home)
        viewModelScope.launch { info = try { Api.info(a) } catch (e: Exception) { null } }
    }

    fun saveAccount(a: Account, activate: Boolean) {
        val l = accounts.toMutableList()
        val i = l.indexOfFirst { it.id == a.id }
        if (i >= 0) l[i] = a else l.add(a)
        accounts = l
        store.saveAccounts(l)
        if (activate || active?.id == a.id) select(a)
    }

    fun deleteAccount(id: String) {
        val l = accounts.filter { it.id != id }
        accounts = l
        store.saveAccounts(l)
        if (active?.id == id) {
            val n = l.firstOrNull()
            if (n != null) select(n) else {
                active = null; info = null; store.activeId = null
                stack.clear(); stack.add(Screen.Accounts)
            }
        }
    }

    /* ---------- data (cached) ---------- */
    private fun acc() = active ?: throw IllegalStateException("no account")

    suspend fun categories(s: Section): List<Category> {
        val a = acc()
        val k = "${a.id}|$s"
        catCache[k]?.let { return it }
        return Api.categories(a, s).also { catCache[k] = it }
    }

    suspend fun items(s: Section, cat: String?): List<Item> {
        val a = acc()
        val k = "${a.id}|$s|$cat"
        itemCache[k]?.let { return it }
        val r = Api.items(a, s, cat)
        itemCache[k] = r
        while (itemCache.size > 5) itemCache.remove(itemCache.keys.first())
        return r
    }

    fun gridState(key: String): LazyGridState {
        if (gridStates.size > 12 && !gridStates.containsKey(key)) gridStates.clear()
        return gridStates.getOrPut(key) { LazyGridState() }
    }

    fun listState(key: String): LazyListState = listStates.getOrPut(key) { LazyListState() }

    /* ---------- play ---------- */
    fun playItemFor(i: Item): PlayItem {
        val a = acc()
        return if (i.section == Section.LIVE)
            PlayItem("l${i.id}", i.name, Api.liveUrl(a, i.id), i.img, true)
        else
            PlayItem("m${i.id}", i.name, Api.movieUrl(a, i.id, i.ext ?: "mp4"), i.img, false)
    }

    fun episodePlaylist(si: SeriesInfo, seriesId: String, name: String, cover: String?): List<PlayItem> {
        val a = acc()
        return si.seasons.toSortedMap().values.flatten().map {
            PlayItem(
                key = "e${it.id}",
                title = "$name - م${it.season} ح${it.num}",
                url = Api.episodeUrl(a, it.id, it.ext),
                img = it.img ?: cover, live = false, seriesName = name, sid = seriesId
            )
        }
    }

    fun openPlayer(items: List<PlayItem>, index: Int, fromStart: Boolean = false) {
        if (index in items.indices) player = PlayerRequest(items, index, fromStart)
    }

    fun closePlayer() { player = null; progressTick++ }

    /** Resume from the "continue watching" list (loads the rest of the series when possible). */
    fun resume(p: Progress) {
        viewModelScope.launch {
            val single = PlayItem(p.key, p.title, p.url, p.img, false, p.seriesName, p.sid)
            val sid = p.sid
            if (sid != null) {
                try {
                    val si = Api.seriesInfo(acc(), sid, p.img)
                    val list = episodePlaylist(si, sid, p.seriesName ?: "", p.img)
                    val idx = list.indexOfFirst { it.key == p.key }
                    if (idx >= 0) { openPlayer(list, idx); return@launch }
                } catch (e: Exception) { /* fall back to the single item */ }
            }
            openPlayer(listOf(single), 0)
        }
    }

    /* ---------- progress ---------- */
    fun progressOf(key: String): Progress? {
        progressTick // subscribe UI to changes
        return active?.let { store.getProgress(it.id, key) }
    }

    fun continueList(): List<Progress> {
        progressTick
        val a = active ?: return emptyList()
        val seen = HashSet<String>()
        return store.allProgress(a.id).filter { !it.done }.sortedByDescending { it.ts }
            .filter { p -> p.sid == null || seen.add(p.sid) }
    }

    fun clearProgress() { active?.let { store.clearProgress(it.id) }; progressTick++ }

    fun saveProgress(p: PlayItem, pos: Long, dur: Long, done: Boolean) {
        val a = active ?: return
        if (p.live) return
        store.putProgress(
            Progress(a.id, p.key, p.title, p.url, p.img, pos, dur, System.currentTimeMillis(), done, p.seriesName, p.sid)
        )
    }
}
