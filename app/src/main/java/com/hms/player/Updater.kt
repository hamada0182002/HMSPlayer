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
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import javax.net.ssl.SSLException

/**
 * >>> غيّر السطر ده مرة واحدة فقط: اسم حسابك على GitHub / اسم المستودع <<<
 * (لازم المستودع يكون Public)
 */
const val GITHUB_REPO = "hamada0182002/HMSPlayer"

data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val size: Long)

/** In-app updater: reads the latest GitHub Release, downloads its .apk and opens the installer. */
object Updater {
    private fun baseBuilder() = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)

    @Volatile private var client: OkHttpClient = baseBuilder().build()
    @Volatile private var clientReady = false

    /**
     * Old TVs (Android 7 and below) don't know GitHub's newer root certificates, so https fails.
     * Fix: trust the phone's own certificates PLUS an up-to-date Mozilla root list bundled in the app
     * (res/raw/cacert.pem). Certificates are still fully validated - nothing is disabled.
     */
    private fun ensureClient(ctx: Context) {
        if (clientReady) return
        synchronized(this) {
            if (clientReady) return
            try {
                val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
                var n = 0
                // 1) the system store
                try {
                    val sys = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
                    for (a in sys.aliases()) sys.getCertificate(a)?.let { ks.setCertificateEntry("sys${n++}", it) }
                } catch (e: Exception) { /* ignore */ }
                // 2) bundled up-to-date roots
                ctx.applicationContext.resources.openRawResource(R.raw.cacert).use { input ->
                    CertificateFactory.getInstance("X.509").generateCertificates(input)
                        .forEach { ks.setCertificateEntry("app${n++}", it) }
                }
                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(ks) }
                val tm = tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
                val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }
                client = baseBuilder().sslSocketFactory(ssl.socketFactory, tm).build()
            } catch (e: Exception) { /* keep the default client */ }
            clientReady = true
        }
    }

    /** GitHub is https-only, so "try http" is not an option here: explain the real causes instead. */
    private fun exec(req: Request) = try {
        client.newCall(req).execute()
    } catch (e: SSLException) {
        val d = ((e.cause ?: e).message ?: e.javaClass.simpleName).take(140)
        throw IOException(
            tr("مشكلة في شهادة الأمان (https) مع GitHub.\n", "Security certificate (https) problem with GitHub.\n") +
            tr("غالباً تاريخ/وقت التلفزيون غلط: اضبطهم على \"تلقائي\" من إعدادات التلفزيون وجرب تاني.\n",
               "The TV date/time is probably wrong: set them to \"automatic\" in the TV settings and try again.\n") +
            tr("ولو مضبوطين يبقى نظام التلفزيون قديم، ثبّت التحديث بفلاشة USB.\n[$d]",
               "If they are correct, the TV system is outdated: install the update from a USB drive.\n[$d]")
        )
    }

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
        ensureClient(ctx)
        if (GITHUB_REPO.startsWith("OWNER")) {
            throw IOException(tr("لسه ما اتحددش مصدر التحديثات (GITHUB_REPO جوه ملف Updater.kt).", "The update source is not set yet (GITHUB_REPO inside Updater.kt)."))
        }
        val req = Request.Builder()
            .url("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", UA)
            .build()
        exec(req).use { r ->
            if (r.code == 404) throw IOException(tr("مفيش إصدارات منشورة لسه.", "No releases published yet."))
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val j = JSONObject(r.body!!.string())
            val tag = j.optString("tag_name").trim().trimStart('v', 'V')
            val assets = j.optJSONArray("assets")
            // pick the APK that belongs to THIS release's version (a release may hold older APKs too)
            var url = ""
            var size = 0L
            var bestScore = -1L
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name")
                if (!name.endsWith(".apk", true)) continue
                val score = (if (name.contains(tag)) 1_000_000_000_000L else 0L) +
                    a.optString("updated_at").filter(Char::isDigit).toLongOrNull().let { it ?: 0L } / 100_000
                if (score > bestScore) {
                    bestScore = score
                    url = a.optString("browser_download_url"); size = a.optLong("size")
                }
            }
            if (tag.isEmpty() || url.isEmpty() || !isNewer(tag, currentVersion(ctx))) null
            else UpdateInfo(tag, j.optString("body").trim(), url, size)
        }
    }

    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            ensureClient(ctx)
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val f = File(dir, "HMS-Player-${info.version}.apk")
            val req = Request.Builder().url(info.apkUrl).header("User-Agent", UA).build()
            exec(req).use { r ->
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
