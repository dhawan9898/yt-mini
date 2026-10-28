package com.nexusstream.app.data

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * "Link with TV code": the YouTube TV app's lounge protocol, as the phone apps use it. A code
 * shown on the TV (Settings > Link with TV code) pairs it; then this app acts as a remote: play a
 * video there, play / pause, seek and skip.
 */
object TvLink {
    data class Screen(val name: String, val screenId: String, val loungeToken: String)

    private const val BASE = "https://www.youtube.com/api/lounge"
    private val deviceId = java.util.UUID.randomUUID().toString()

    private var screen: Screen? = null
    private var sid: String? = null
    private var gsession: String? = null
    private var rid = 1000
    private var ofs = 0

    private val _linked = kotlinx.coroutines.flow.MutableStateFlow<Screen?>(null)
    /** The paired TV, if any (kept across restarts). */
    val linked: kotlinx.coroutines.flow.StateFlow<Screen?> = _linked

    fun restore() {
        val raw = LibraryRepository.loadBlob("tv_screen") ?: return
        runCatching {
            val o = JSONObject(raw)
            Screen(o.getString("name"), o.getString("screenId"), o.getString("loungeToken"))
        }.getOrNull()?.let { screen = it; _linked.value = it }
    }

    /** Pairs with the TV showing [code] (digits, spaces allowed). */
    fun pair(code: String): Screen {
        val digits = code.filter(Char::isDigit)
        require(digits.length >= 6) { "Enter the code shown on your TV" }
        val res = post("$BASE/pairing/get_screen", mapOf("pairing_code" to digits))
        val s = JSONObject(res).optJSONObject("screen") ?: error("That code didn't work. Check it on your TV.")
        val linked = Screen(
            name = s.optString("name").ifBlank { "YouTube on TV" },
            screenId = s.getString("screenId"),
            loungeToken = s.getString("loungeToken")
        )
        screen = linked
        sid = null
        _linked.value = linked
        LibraryRepository.saveBlob("tv_screen", JSONObject().put("name", linked.name).put("screenId", linked.screenId).put("loungeToken", linked.loungeToken).toString())
        return linked
    }

    fun unlink() {
        screen = null
        sid = null
        _linked.value = null
        LibraryRepository.saveBlob("tv_screen", "")
    }

    /** Plays [videoIds] on the TV, starting at [index] and [startSeconds]. */
    fun play(videoIds: List<String>, index: Int = 0, startSeconds: Long = 0) {
        val ids = videoIds.take(50)
        send(
            "setPlaylist",
            mapOf(
                "videoId" to ids[index.coerceIn(0, ids.size - 1)],
                "videoIds" to ids.joinToString(","),
                "currentIndex" to index.toString(),
                "currentTime" to startSeconds.toString()
            )
        )
    }

    fun resume() = send("play", emptyMap())
    fun pause() = send("pause", emptyMap())
    fun next() = send("next", emptyMap())
    fun previous() = send("previous", emptyMap())
    fun seekTo(seconds: Long) = send("seekTo", mapOf("newTime" to seconds.toString()))

    @Synchronized
    private fun send(command: String, args: Map<String, String>) {
        if (sid == null) connect()
        val form = LinkedHashMap<String, String>()
        form["count"] = "1"
        form["ofs"] = ofs.toString()
        form["req0__sc"] = command
        args.forEach { (k, v) -> form["req0_$k"] = v }
        val ok = runCatching { post(bindUrl(withSession = true), form) }.isSuccess
        if (!ok) {
            // The session expired (TVs drop idle remotes): bind again once and retry.
            connect()
            form["ofs"] = ofs.toString()
            post(bindUrl(withSession = true), form)
        }
        ofs++
    }

    private fun connect() {
        val s = screen ?: error("No TV linked. Use Link with TV code in Settings.")
        val text = runCatching { post(bindUrl(withSession = false), mapOf("count" to "0")) }.getOrNull()
        val refreshed = text == null
        if (refreshed) {
            // The lounge token expires after a while: get a fresh one for the paired screen.
            val res = JSONObject(post("$BASE/pairing/get_lounge_token_batch", mapOf("screen_ids" to s.screenId)))
            val token = res.optJSONArray("screens")?.optJSONObject(0)?.optString("loungeToken")?.takeIf { it.isNotEmpty() }
                ?: error("Couldn't reach the TV. Link it again.")
            screen = s.copy(loungeToken = token)
        }
        val body = text ?: post(bindUrl(withSession = false), mapOf("count" to "0"))
        val (newSid, newGsession) = parseBind(body)
        sid = newSid ?: error("The TV didn't answer")
        gsession = newGsession
        ofs = 0
    }

    private fun bindUrl(withSession: Boolean): String {
        val s = screen ?: error("No TV linked")
        val b = "$BASE/bc/bind".toHttpUrl().newBuilder()
            .addQueryParameter("device", "REMOTE_CONTROL")
            .addQueryParameter("id", deviceId)
            .addQueryParameter("name", "yt-mini")
            .addQueryParameter("app", "youtube-desktop")
            .addQueryParameter("mdx-version", "3")
            .addQueryParameter("loungeIdToken", s.loungeToken)
            .addQueryParameter("VER", "8")
            .addQueryParameter("v", "2")
            .addQueryParameter("RID", (rid++).toString())
            .addQueryParameter("zx", java.lang.Long.toString(System.nanoTime(), 36))
            .addQueryParameter("t", "1")
        if (withSession) {
            b.addQueryParameter("SID", sid)
            gsession?.let { b.addQueryParameter("gsessionid", it) }
            b.addQueryParameter("AID", "0")
        } else {
            b.addQueryParameter("CVER", "1")
        }
        return b.build().toString()
    }

    /** The bind answer is length-prefixed JSON chunks; the session id is in ["c", SID, ...], the gsession in ["S", id]. */
    internal fun parseBind(text: String): Pair<String?, String?> {
        var sid: String? = null
        var gsession: String? = null
        val start = text.indexOf('[')
        if (start < 0) return null to null
        // Chunks are concatenated arrays; read them one by one.
        var i = start
        while (i < text.length) {
            val end = matchingBracket(text, i)
            if (end < 0) break
            runCatching { JSONArray(text.substring(i, end + 1)) }.getOrNull()?.let { arr -> scan(arr) { k, v ->
                if (k == "c" && sid == null) sid = v
                if (k == "S" && gsession == null) gsession = v
            } }
            i = text.indexOf('[', end + 1).takeIf { it >= 0 } ?: break
        }
        return sid to gsession
    }

    private fun scan(arr: JSONArray, found: (String, String) -> Unit) {
        // [[0, ["c", "SID", "", 8]], [1, ["S", "gsession"]], ...]
        for (i in 0 until arr.length()) {
            val item = arr.optJSONArray(i) ?: continue
            val payload = item.optJSONArray(1) ?: continue
            val key = payload.optString(0)
            val value = payload.opt(1) as? String ?: continue
            found(key, value)
        }
    }

    private fun matchingBracket(s: String, open: Int): Int {
        var depth = 0
        var inString = false
        var i = open
        while (i < s.length) {
            val c = s[i]
            if (inString) {
                if (c == '\\') i++ else if (c == '"') inString = false
            } else when (c) {
                '"' -> inString = true
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) return i }
            }
            i++
        }
        return -1
    }

    private fun post(url: String, form: Map<String, String>): String {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        val request = Request.Builder().url(url).post(body).header("User-Agent", NewPipeDownloader.USER_AGENT).build()
        NewPipeDownloader.instance.client.newCall(request).execute().use { res ->
            check(res.isSuccessful) { "TV link failed (${res.code})" }
            return res.body?.string().orEmpty()
        }
    }
}
