package com.nexusstream.app.ui.youtube

/** A tappable piece of a video description. */
data class DescriptionLink(
    val start: Int,
    val end: Int,
    val kind: Kind,
    /** The URL for [Kind.URL]; the position in milliseconds (as text) for [Kind.TIMESTAMP]. */
    val value: String
) {
    enum class Kind { URL, TIMESTAMP }
}

/** Finds web links and "1:23" / "1:02:03" timestamps in description text. Pure, no Android. */
internal object DescriptionLinks {
    private val URL = Regex("""https?://[^\s]+""")
    private val TIMESTAMP = Regex("""(?<![\w:./])(?:(\d{1,2}):)?([0-5]?\d):([0-5]\d)(?![\w:])""")

    /** Position in milliseconds for the parts of a timestamp (hours may be null). */
    fun timestampMs(hours: String?, minutes: String, seconds: String): Long =
        ((hours?.toLongOrNull() ?: 0L) * 3600 + minutes.toLong() * 60 + seconds.toLong()) * 1000

    fun find(text: String): List<DescriptionLink> {
        val links = mutableListOf<DescriptionLink>()
        URL.findAll(text).forEach { m ->
            // Drop punctuation that usually trails a link in prose.
            val trimmed = m.value.trimEnd('.', ',', ')', ';', '!', '?')
            links += DescriptionLink(m.range.first, m.range.first + trimmed.length, DescriptionLink.Kind.URL, trimmed)
        }
        TIMESTAMP.findAll(text).forEach { m ->
            val overlapsUrl = links.any { it.kind == DescriptionLink.Kind.URL && m.range.first < it.end && m.range.last >= it.start }
            if (!overlapsUrl) {
                val ms = timestampMs(m.groupValues[1].ifEmpty { null }, m.groupValues[2], m.groupValues[3])
                links += DescriptionLink(m.range.first, m.range.last + 1, DescriptionLink.Kind.TIMESTAMP, ms.toString())
            }
        }
        return links.sortedBy { it.start }
    }
}
