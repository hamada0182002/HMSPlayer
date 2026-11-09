package com.hms.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * >>> غيّر السطر ده مرة واحدة فقط: اسم حسابك على GitHub / اسم المستودع <<<
 * (لازم المستودع يكون Public)
 */
const val GITHUB_REPO = "hamada0182002/HMSPlayer"

data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val size: Long)

/** In-app updater: reads the latest GitHub Release, downloads its .apk and opens the installer. */
object Updater {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun currentVersion(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }

    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val l = local.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** null = already on the latest version. */
    suspend fun check(ctx: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        if (GITHUB_REPO.startsWith("OWNER")) {
            throw IOException("لسه ما اتحددش مصدر التحديثات (GITHUB_REPO جوه ملف Updater.kt).")
        }
        val req = Request.Builder()
            .url("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", UA)
            .build()
        client.newCall(req).execute().use { r ->
            if (r.code == 404) throw IOException("مفيش إصدارات منشورة لسه.")
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val j = JSONObject(r.body!!.string())
            val tag = j.optString("tag_name").trim().trimStart('v', 'V')
            val assets = j.optJSONArray("assets")
            var url = ""
            var size = 0L
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                if (a.optString("name").endsWith(".apk", true)) {
                    url = a.optString("browser_download_url"); size = a.optLong("size"); break
                }
            }
            if (tag.isEmpty() || url.isEmpty() || !isNewer(tag, currentVersion(ctx))) null
            else UpdateInfo(tag, j.optString("body").trim(), url, size)
        }
    }

    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val f = File(dir, "HMS-Player-${info.version}.apk")
            val req = Request.Builder().url(info.apkUrl).header("User-Agent", UA).build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                val body = r.body!!
                val total = body.contentLength().takeIf { it > 0 } ?: info.size
                body.byteStream().use { input ->
                    f.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        var last = -1
                        while (true) {
                            ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val p = (read * 100 / total).toInt()
                                if (p != last) { last = p; onProgress(p / 100f) }
                            }
                        }
                    }
                }
            }
            f
        }

    /** false = Android needs the user to allow "install unknown apps" first (settings page is opened). */
    fun install(ctx: Context, f: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}