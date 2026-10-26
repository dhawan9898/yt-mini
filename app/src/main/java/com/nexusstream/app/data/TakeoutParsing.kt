package com.nexusstream.app.data

import com.nexusstream.app.model.MediaItemModel
import org.json.JSONArray

/** Parsers for files from a Google Takeout export of YouTube data. Pure logic, no Android. */
object TakeoutParsing {

    private val VIDEO_ID = Regex("[?&]v=([\\w-]{11})")

    /** Splits one CSV line, honouring "quoted, fields" and doubled quotes inside them. */
    internal fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields += current.toString()
        return fields
    }

    /** Parses Takeout's `subscriptions.csv` ("Channel Id,Channel Url,Channel Title"). */
    fun parseSubscriptionsCsv(text: String): List<LibraryRepository.ChannelSub> {
        val lines = text.removePrefix("﻿").lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()
        val header = parseCsvLine(lines.first()).map { it.trim().lowercase() }
        val idCol = header.indexOf("channel id")
        val urlCol = header.indexOf("channel url")
        val titleCol = header.indexOf("channel title")
        if (titleCol < 0 || (idCol < 0 && urlCol < 0)) return emptyList()

        return lines.drop(1).mapNotNull { line ->
            val cols = parseCsvLine(line.trimEnd('\r'))
            val title = cols.getOrNull(titleCol)?.trim().orEmpty()
            val id = cols.getOrNull(idCol)?.trim().orEmpty()
            val url = cols.getOrNull(urlCol)?.trim().orEmpty()
            if (title.isEmpty() || (id.isEmpty() && url.isEmpty())) {
                null
            } else {
                LibraryRepository.ChannelSub(
                    name = title,
                    avatarUrl = "",
                    channelUrl = if (id.isNotEmpty()) "https://www.youtube.com/channel/$id" else url
                )
            }
        }
    }

    /**
     * Parses Takeout's `watch-history.json`. Skips YouTube Music entries and entries without a
     * video link (removed videos, posts). Entries are newest first, as exported.
     */
    fun parseWatchHistory(json: String): List<MediaItemModel> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val items = mutableListOf<MediaItemModel>()
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            if (entry.optString("header") == "YouTube Music") continue
            val id = VIDEO_ID.find(entry.optString("titleUrl"))?.groupValues?.get(1) ?: continue
            val title = entry.optString("title").removePrefix("Watched ").trim()
            if (title.isEmpty()) continue
            val channel = entry.optJSONArray("subtitles")?.optJSONObject(0)
            items += MediaItemModel(
                id = id,
                title = title,
                artistOrChannel = channel?.optString("name").orEmpty(),
                audioUrl = "",
                videoUrl = "",
                artworkUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                durationMs = 0L,
                sourceUrl = "https://www.youtube.com/watch?v=$id",
                channelUrl = channel?.optString("url").orEmpty()
            )
        }
        return items.distinctBy { it.id }
    }
}
