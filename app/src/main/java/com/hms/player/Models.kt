package com.hms.player

enum class DeviceMode { PHONE, TV }

enum class Section(private val ar: String, private val en: String) {
    LIVE("مباشر", "Live"), VOD("أفلام", "Movies"), SERIES("مسلسلات", "Series");

    /** Section name in the selected app language. */
    val title: String get() = tr(ar, en)
}

data class Account(
    val id: String,
    val name: String,
    val server: String,
    val user: String,
    val pass: String
)

data class Category(val id: String, val name: String)

data class Item(
    val id: String,
    val name: String,
    val img: String?,
    val section: Section,
    val rating: String? = null,
    val ext: String? = null,
    val plot: String? = null,
    val added: Long = 0
)

data class Episode(
    val id: String,
    val season: Int,
    val num: Int,
    val title: String,
    val ext: String,
    val img: String?,
    val duration: String?
)

data class SeriesInfo(
    val plot: String,
    val meta: String,
    val cover: String?,
    val seasons: Map<Int, List<Episode>>
)

data class VodInfo(val plot: String, val meta: String, val img: String?)

data class AccountInfo(
    val status: String,
    val expiry: Long?,
    val maxConn: String,
    val activeConn: String
)

/** Anything the player can play (channel / movie / episode). */
data class PlayItem(
    val key: String,
    val title: String,
    val url: String,
    val img: String?,
    val live: Boolean,
    val seriesName: String? = null,
    val sid: String? = null
)

data class Progress(
    val accountId: String,
    val key: String,
    val title: String,
    val url: String,
    val img: String?,
    val pos: Long,
    val dur: Long,
    val ts: Long,
    val done: Boolean,
    val seriesName: String?,
    val sid: String?
)

class PlayerRequest(val items: List<PlayItem>, val index: Int, val fromStart: Boolean = false)

sealed interface Screen {
    data object Accounts : Screen
    data object Home : Screen
    data object Continue : Screen
    data object Settings : Screen
    data class Browse(val section: Section) : Screen
    data class Movie(val item: Item) : Screen
    data class Series(val item: Item) : Screen
}
