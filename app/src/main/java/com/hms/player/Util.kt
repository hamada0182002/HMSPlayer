package com.hms.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.CancellationException
import org.json.JSONException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLException

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = s % 3600 / 60
    val c = s % 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.US, h, m, c) else "%d:%02d".format(Locale.US, m, c)
}

private val diacritics = Regex("[\\u064B-\\u0652\\u0640]")

/** Arabic-friendly normalisation used by the search box. */
fun norm(t: String): String = t.lowercase()
    .replace(diacritics, "")
    .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
    .replace('ة', 'ه').replace('ى', 'ي')

fun friendly(e: Throwable): String = when (e) {
    is AuthException -> tr("اليوزر أو الباسورد غلط، أو الاشتراك منتهي.", "Wrong username or password, or the subscription has expired.")
    is SocketTimeoutException -> tr("السيرفر اتأخر في الرد. جرب تاني.", "The server took too long to respond. Try again.")
    is UnknownHostException -> tr("مش قادر يوصل للسيرفر. اتأكد من الرابط ومن الإنترنت.", "Can't reach the server. Check the URL and your internet connection.")
    is ConnectException -> tr("السيرفر مش بيرد على العنوان ده. اتأكد من الرابط والبورت.", "The server is not responding at this address. Check the URL and port.")
    is SSLException -> tr("مشكلة في شهادة الأمان (https). جرب الرابط بـ http.", "Security certificate (https) problem. Try the link with http.")
    is JSONException -> tr("السيرفر رجّع رد غير مفهوم. اتأكد إنه سيرفر Xtream.", "The server returned an unreadable response. Make sure it is an Xtream server.")
    is IllegalArgumentException -> tr("رابط السيرفر غير صحيح.", "The server URL is not valid.")
    else -> e.message?.takeIf { it.isNotBlank() }?.let { tr("حصل خطأ: $it", "Error: $it") } ?: tr("حصل خطأ غير معروف", "Unknown error")
}

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Err(val msg: String) : Load<Nothing>
    data class Ok<T>(val v: T) : Load<T>
}

/** Runs [block] when [key] / [retry] change and exposes Loading / Err / Ok. */
@Composable
fun <T> produceLoad(key: Any?, retry: Int = 0, block: suspend () -> T): Load<T> {
    val s = produceState<Load<T>>(Load.Loading, key, retry) {
        value = Load.Loading
        value = try {
            Load.Ok(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Err(friendly(e))
        }
    }
    return s.value
}

/** Phone held vertically (narrow) -> compact layouts. Wide screens (landscape / TV) use the wide layouts. */
@Composable
fun isCompact(): Boolean = LocalConfiguration.current.screenWidthDp < 600
