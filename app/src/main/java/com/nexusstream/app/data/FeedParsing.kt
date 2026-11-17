package com.nexusstream.app.data

import java.time.OffsetDateTime

/** One video from a channel's public RSS feed. */
data class RssEntry(
    val videoId: String,
    val title: String,
    val channelName: String,
    val publishedAtMs: Long,
    val views: Long
)

/** Pure helpers for the subscriptions feed (no Android or network dependencies). */
object FeedParsing {

    private val ENTRY = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
    private val VIDEO_ID = Regex("<yt:videoId>([^<]+)</yt:videoId>")
    private val TITLE = Regex("<title>([^<]*)</title>")
    private val PUBLISHED = Regex("<published>([^<]+)</published>")
    private val AUTHOR = Regex("<author>\\s*<name>([^<]*)</name>", RegexOption.DOT_MATCHES_ALL)
    private val VIEWS = Regex("<media:statistics views=\"(\\d+)\"")
    private val CHANNEL_ID = Regex("/channel/(UC[\\w-]{22})")

    /** Extracts the channel id (UC...) from a channel URL, or null. */
    fun channelId(channelUrl: String): String? = CHANNEL_ID.find(channelUrl)?.groupValues?.get(1)

    fun feedUrl(channelId: String) = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"

    /** Parses a YouTube channel Atom feed; malformed entries are skipped. */
    fun parseFeed(xml: String): List<RssEntry> = ENTRY.findAll(xml).mapNotNull { match ->
        val body = match.groupValues[1]
        val id = VIDEO_ID.find(body)?.groupValues?.get(1) ?: return@mapNotNull null
        val published = PUBLISHED.find(body)?.groupValues?.get(1)
            ?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
            ?: return@mapNotNull null
        RssEntry(
            videoId = id,
            title = unescape(TITLE.find(body)?.groupValues?.get(1).orEmpty()),
            channelName = unescape(AUTHOR.find(body)?.groupValues?.get(1).orEmpty()),
            publishedAtMs = published,
            views = VIEWS.find(body)?.groupValues?.get(1)?.toLongOrNull() ?: -1L
        )
    }.toList()

    private fun unescape(text: String): String = text
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&amp;", "&")

    /**
     * The upload date as YouTube shows it ("6 months ago"). The extractor gives the watch page's date
     * as an ISO timestamp ("2026-04-29T05:56:03-07:00"); text that is already relative passes through.
     */
    fun uploadText(textual: String?, nowMs: Long = System.currentTimeMillis()): String {
        val text = textual?.trim().orEmpty()
        if (!Regex("""^\d{4}-\d{2}-\d{2}""").containsMatchIn(text)) return text
        val ms = runCatching { java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli() }
            .recoverCatching { java.time.Instant.parse(text).toEpochMilli() }
            .recoverCatching { java.time.LocalDate.parse(text.take(10)).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }
            .getOrNull() ?: return text
        return relativeTime(ms, nowMs)
    }

    /**
     * A large version of a cover for the now-playing screen. YouTube Music art (googleusercontent /
     * ggpht) is served at the size its URL asks for ("=w60-h60-l90-rj"), and lists ask for a small one.
     */
    fun largeArtwork(url: String, size: Int = 1080): String {
        if (!url.contains("googleusercontent.com") && !url.contains("ggpht.com")) return url
        val sized = Regex("""=w\d+-h\d+[^/?#]*$""")
        return if (sized.containsMatchIn(url)) url.replace(sized, "=w$size-h$size-l90-rj")
        else if (!url.substringAfterLast('/').contains('=')) "$url=w$size-h$size-l90-rj"
        else url
    }

    /** "3 days ago" style text for a publish time. */
    fun relativeTime(publishedAtMs: Long, nowMs: Long): String {
        val seconds = ((nowMs - publishedAtMs) / 1000).coerceAtLeast(0)
        val units = listOf(
            31_536_000L to "year", 2_592_000L to "month", 604_800L to "week",
            86_400L to "day", 3_600L to "hour", 60L to "minute"
        )
        for ((size, name) in units) {
            val count = seconds / size
            if (count >= 1) return "$count $name${if (count == 1L) "" else "s"} ago"
        }
        return "just now"
    }
}
