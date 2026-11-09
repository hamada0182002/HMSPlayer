package com.hms.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Accounts + watch progress, kept in SharedPreferences as JSON. */
class Store(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("hms_player", Context.MODE_PRIVATE)

    fun accounts(): List<Account> = try {
        val a = JSONArray(sp.getString("accounts", "[]"))
        (0 until a.length()).mapNotNull { i ->
            a.optJSONObject(i)?.let {
                Account(it.getString("id"), it.getString("name"), it.getString("server"), it.getString("user"), it.getString("pass"))
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveAccounts(list: List<Account>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("server", it.server).put("user", it.user).put("pass", it.pass))
        }
        sp.edit().putString("accounts", a.toString()).apply()
    }

    var activeId: String?
        get() = sp.getString("active", null)
        set(v) { sp.edit().putString("active", v).apply() }

    var resizeMode: Int
        get() = sp.getInt("resize", 0)
        set(v) { sp.edit().putInt("resize", v).apply() }

    private var prog: MutableMap<String, Progress>? = null

    private fun load(): MutableMap<String, Progress> {
        prog?.let { return it }
        val m = LinkedHashMap<String, Progress>()
        try {
            val a = JSONArray(sp.getString("progress", "[]"))
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val p = Progress(
                    o.getString("aid"), o.getString("key"), o.optString("title"), o.optString("url"),
                    o.optString("img").ifBlank { null }, o.optLong("pos"), o.optLong("dur"), o.optLong("ts"),
                    o.optBoolean("done"), o.optString("sname").ifBlank { null }, o.optString("sid").ifBlank { null }
                )
                m["${p.accountId}|${p.key}"] = p
            }
        } catch (e: Exception) { /* ignore corrupted data */ }
        prog = m
        return m
    }

    fun getProgress(aid: String, key: String): Progress? = load()["$aid|$key"]

    fun allProgress(aid: String): List<Progress> = load().values.filter { it.accountId == aid }

    fun putProgress(p: Progress) {
        val m = load()
        m["${p.accountId}|${p.key}"] = p
        if (m.size > 400) {
            m.values.sortedBy { it.ts }.take(m.size - 400).forEach { m.remove("${it.accountId}|${it.key}") }
        }
        flush()
    }

    fun clearProgress(aid: String) {
        val m = load()
        m.keys.filter { it.startsWith("$aid|") }.forEach { m.remove(it) }
        flush()
    }

    private fun flush() {
        val a = JSONArray()
        load().values.forEach {
            a.put(
                JSONObject().put("aid", it.accountId).put("key", it.key).put("title", it.title).put("url", it.url)
                    .put("img", it.img ?: "").put("pos", it.pos).put("dur", it.dur).put("ts", it.ts)
                    .put("done", it.done).put("sname", it.seriesName ?: "").put("sid", it.sid ?: "")
            )
        }
        sp.edit().putString("progress", a.toString()).apply()
    }
}
