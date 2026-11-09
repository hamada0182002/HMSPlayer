package com.hms.player

import android.net.Uri
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val UA = "HMSPlayer/1.0"

class AuthException : IOException("auth")

private fun JSONObject.str(k: String): String {
    val v = opt(k)
    return if (v == null || v == JSONObject.NULL) "" else v.toString()
}

/** Xtream Codes client (same endpoints the old TV page used). */
object Api {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun apiUrl(a: Account, action: String? = null, extra: String = ""): String =
        "${a.server}/player_api.php?username=${enc(a.user)}&password=${enc(a.pass)}" +
            (if (action != null) "&action=$action" else "") + extra

    private suspend fun fetch(url: String, readSec: Long): String =
        suspendCancellableCoroutine { cont ->
            val call = client.newBuilder().readTimeout(readSec, TimeUnit.SECONDS).build()
                .newCall(Request.Builder().url(url).header("User-Agent", UA).build())
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { r ->
                        if (!r.isSuccessful) {
                            if (cont.isActive) cont.resumeWithException(IOException("HTTP ${r.code}"))
                            return
                        }
                        try {
                            val body = r.body!!.string()
                            if (cont.isActive) cont.resume(body)
                        } catch (e: Exception) {
                            if (cont.isActive) cont.resumeWithException(e)
                        }
                    }
                }
            })
        }

    suspend fun info(a: Account): AccountInfo {
        val j = JSONObject(fetch(apiUrl(a), 30))
        val u = j.optJSONObject("user_info") ?: throw AuthException()
        if (u.str("auth") == "0") throw AuthException()
        return AccountInfo(
            status = u.str("status"),
            expiry = u.str("exp_date").toLongOrNull(),
            maxConn = u.str("max_connections"),
            activeConn = u.str("active_cons")
        )
    }

    private fun action(s: Section) = when (s) {
        Section.LIVE -> "get_live_categories" to "get_live_streams"
        Section.VOD -> "get_vod_categories" to "get_vod_streams"
        Section.SERIES -> "get_series_categories" to "get_series"
    }

    suspend fun categories(a: Account, s: Section): List<Category> {
        val arr = JSONArray(fetch(apiUrl(a, action(s).first), 60))
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { Category(it.str("category_id"), it.str("category_name")) }
        }
    }

    /** [catId] == null loads the whole section (heavy, used only by "search all"). */
    suspend fun items(a: Account, s: Section, catId: String?): List<Item> {
        val extra = if (catId == null) "" else "&category_id=${enc(catId)}"
        val txt = fetch(apiUrl(a, action(s).second, extra), if (catId == null) 300 else 120)
        val arr = JSONArray(txt)
        val out = ArrayList<Item>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += when (s) {
                Section.LIVE -> Item(o.str("stream_id"), o.str("name"), o.str("stream_icon").ifBlank { null }, s)
                Section.VOD -> Item(
                    o.str("stream_id"), o.str("name"), o.str("stream_icon").ifBlank { null }, s,
                    rating = o.str("rating"), ext = o.str("container_extension").ifBlank { "mp4" },
                    added = o.str("added").toLongOrNull() ?: 0
                )
                Section.SERIES -> Item(
                    o.str("series_id"), o.str("name"), o.str("cover").ifBlank { null }, s,
                    rating = o.str("rating"), plot = o.str("plot"),
                    added = o.str("last_modified").toLongOrNull() ?: o.str("added").toLongOrNull() ?: 0
                )
            }
        }
        return out
    }

    private fun meta(r: String, y: String, g: String, du: String): String {
        val a = ArrayList<String>()
        if ((r.toFloatOrNull() ?: 0f) > 0f) a += "التقييم: $r/10"
        if (y.length >= 4) a += y.take(4)
        if (g.isNotBlank()) a += g
        if (du.isNotBlank()) a += du
        return a.joinToString("  •  ")
    }

    suspend fun vodInfo(a: Account, id: String): VodInfo {
        val j = JSONObject(fetch(apiUrl(a, "get_vod_info", "&vod_id=$id"), 30))
        val i = j.optJSONObject("info") ?: return VodInfo("", "", null)
        return VodInfo(
            plot = i.str("plot").ifBlank { i.str("description") },
            meta = meta(
                i.str("rating"),
                i.str("releasedate").ifBlank { i.str("release_date") }.ifBlank { i.str("year") },
                i.str("genre"), i.str("duration")
            ),
            img = i.str("movie_image").ifBlank { null }
        )
    }

    suspend fun seriesInfo(a: Account, id: String, cover: String?): SeriesInfo {
        val j = JSONObject(fetch(apiUrl(a, "get_series_info", "&series_id=$id"), 60))
        val info = j.optJSONObject("info")
        val seasons = sortedMapOf<Int, MutableList<Episode>>()

        fun add(season: Int, o: JSONObject) {
            val inf = o.optJSONObject("info")
            val s = o.str("season").toIntOrNull() ?: season
            seasons.getOrPut(s) { mutableListOf() }.add(
                Episode(
                    id = o.str("id"), season = s,
                    num = o.str("episode_num").toIntOrNull() ?: 0,
                    title = o.str("title"),
                    ext = o.str("container_extension").ifBlank { "mp4" },
                    img = inf?.str("movie_image")?.ifBlank { null },
                    duration = inf?.str("duration")?.ifBlank { null }
                )
            )
        }

        when (val e = j.opt("episodes")) {
            is JSONObject -> e.keys().forEach { k ->
                val arr = e.optJSONArray(k) ?: return@forEach
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { add(k.toIntOrNull() ?: 1, it) }
            }
            is JSONArray -> for (i in 0 until e.length()) {
                val arr = e.optJSONArray(i) ?: continue
                for (n in 0 until arr.length()) arr.optJSONObject(n)?.let { add(i + 1, it) }
            }
            else -> {}
        }
        return SeriesInfo(
            plot = info?.str("plot") ?: "",
            meta = if (info == null) "" else meta(
                info.str("rating"),
                info.str("releaseDate").ifBlank { info.str("releasedate") },
                info.str("genre"), ""
            ),
            cover = info?.str("cover")?.ifBlank { null } ?: cover,
            seasons = seasons.mapValues { (_, l) -> l.sortedBy { it.num } }
        )
    }

    fun liveUrl(a: Account, id: String) = "${a.server}/live/${enc(a.user)}/${enc(a.pass)}/$id.m3u8"
    fun movieUrl(a: Account, id: String, ext: String) = "${a.server}/movie/${enc(a.user)}/${enc(a.pass)}/$id.$ext"
    fun episodeUrl(a: Account, id: String, ext: String) = "${a.server}/series/${enc(a.user)}/${enc(a.pass)}/$id.$ext"

    /** Cleans what the user typed. Also understands a pasted get.php / player_api.php link. */
    fun buildAccount(id: String?, name: String, server: String, user: String, pass: String): Account {
        var s = server.replace(Regex("\\s"), "")
        var u = user.trim()
        var p = pass.trim()
        if (!Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(s)) s = "http://$s"
        val uri = Uri.parse(s)
        if (u.isBlank()) u = uri.getQueryParameter("username") ?: ""
        if (p.isBlank()) p = uri.getQueryParameter("password") ?: ""
        if (s.contains("?") || (uri.path ?: "").contains(".php")) s = "${uri.scheme}://${uri.authority}"
        s = s.trimEnd('/')
        val n = name.trim().ifBlank { Uri.parse(s).host ?: "حساب" }
        return Account(id ?: UUID.randomUUID().toString(), n, s, u, p)
    }
}
