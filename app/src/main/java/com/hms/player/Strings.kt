package com.hms.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.LayoutDirection

enum class AppLanguage(val code: String) { AR("ar"), EN("en") }

/**
 * App language. [current] is Compose state, so every screen that calls [tr] redraws
 * as soon as the language is changed from Settings.
 */
object Lang {
    var current by mutableStateOf(AppLanguage.AR)
    val isAr: Boolean get() = current == AppLanguage.AR
    val direction: LayoutDirection get() = if (isAr) LayoutDirection.Rtl else LayoutDirection.Ltr
    fun fromCode(c: String?): AppLanguage = if (c == "en") AppLanguage.EN else AppLanguage.AR
}

/** Picks the Arabic or English text according to the selected language. */
fun tr(ar: String, en: String): String = if (Lang.isAr) ar else en
