package com.nexusstream.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Live chat (and chat replay of past streams), polled the way the website does: each answer
 * carries the messages since the last one and the token and delay for the next poll.
 */
object LiveChat {
    /** Where a video's chat starts; [replay] is chat replay of a finished stream. */
    data class Start(val continuation: String, val replay: Boolean, val topChat: String?)

    data class Message(
        val id: String,
        val author: String,
        val avatarUrl: String,
        val text: String,
        /** Super Chat / Super Sticker amount ("$5.00"), empty for normal messages. */
        val amount: String,
        val isMember: Boolean,
        val isModerator: Boolean,
        /** Replays: position in the video this message belongs to. */
        val offsetMs: Long
    )

    class Page(val messages: List<Message>, val continuation: String?, val pollAfterMs: Long)

    internal fun parseStart(next: JSONObject): Start? {
        val renderer = find(next, "liveChatRenderer") ?: return null
        val token = renderer.optJSONArray("continuations")?.optJSONObject(0)
            ?.optJSONObject("reloadContinuationData")?.optString("continuation")?.takeIf { it.isNotEmpty() } ?: return null
        // The header's "Top chat / Live chat" menu: the second entry is the full chat.
        val menu = find(renderer.optJSONObject("header"), "sortFilterSubMenuRenderer")?.optJSONArray("subMenuItems")
        val all = menu?.optJSONObject(1)?.let { find(it, "reloadContinuationData")?.optString("continuation") }?.takeIf { it.isNotEmpty() }
        return Start(all ?: token, renderer.optBoolean("isReplay"), token.takeIf { all != null })
    }

    /** The next batch of messages. For replays, [offsetMs] is the current playback position. */
    fun poll(continuation: String, replay: Boolean, offsetMs: Long): Page {
        val body = JSONObject().put("continuation", continuation)
        if (replay) body.put("currentPlayerState", JSONObject().put("playerOffsetMs", offsetMs.toString()))
        return parse(InnerTube.web(if (replay) "live_chat/get_live_chat_replay" else "live_chat/get_live_chat", body))
    }

    internal fun parse(root: JSONObject): Page {
        val chat = root.optJSONObject("continuationContents")?.optJSONObject("liveChatContinuation") ?: return Page(emptyList(), null, 5000)
        val messages = ArrayList<Message>()
        chat.optJSONArray("actions")?.let { actions ->
            for (i in 0 until actions.length()) {
                val action = actions.optJSONObject(i) ?: continue
                val replay = action.optJSONObject("replayChatItemAction")
                if (replay != null) {
                    val offset = replay.optString("videoOffsetTimeMsec").toLongOrNull() ?: 0L
                    val inner = replay.optJSONArray("actions") ?: JSONArray()
                    for (j in 0 until inner.length()) inner.optJSONObject(j)?.let { add(it, offset, messages) }
                } else {
                    add(action, 0L, messages)
                }
            }
        }
        val cont = chat.optJSONArray("continuations")?.optJSONObject(0)
        val data = cont?.let { c ->
            c.optJSONObject("invalidationContinuationData") ?: c.optJSONObject("timedContinuationData")
                ?: c.optJSONObject("liveChatReplayContinuationData") ?: c.optJSONObject("reloadContinuationData")
        }
        val token = data?.optString("continuation")?.takeIf { it.isNotEmpty() }
        val wait = data?.optLong("timeoutMs", 0L)?.takeIf { it > 0 } ?: if (token != null) 1500L else 5000L
        return Page(messages, token, wait.coerceIn(500L, 10_000L))
    }

    private fun add(action: JSONObject, offset: Long, out: MutableList<Message>) {
        val item = action.optJSONObject("addChatItemAction")?.optJSONObject("item") ?: return
        val r = item.optJSONObject("liveChatTextMessageRenderer")
            ?: item.optJSONObject("liveChatPaidMessageRenderer")
            ?: item.optJSONObject("liveChatPaidStickerRenderer")
            ?: item.optJSONObject("liveChatMembershipItemRenderer")
            ?: return
        val badges = r.optJSONArray("authorBadges")?.toString().orEmpty()
        val text = InnerTube.text(r.optJSONObject("message")).ifEmpty { InnerTube.text(r.optJSONObject("headerSubtext")) }
        out += Message(
            id = r.optString("id"),
            author = InnerTube.text(r.optJSONObject("authorName")),
            avatarUrl = InnerTube.lastThumbnail(r.optJSONObject("authorPhoto")),
            text = text,
            amount = InnerTube.text(r.optJSONObject("purchaseAmountText")),
            isMember = badges.contains("Member", ignoreCase = true) || item.has("liveChatMembershipItemRenderer"),
            isModerator = badges.contains("MODERATOR"),
            offsetMs = offset
        )
    }

    private fun find(node: Any?, key: String): JSONObject? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject(key)?.let { return it }
                for (k in node.keys()) find(node.opt(k), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) find(node.opt(i), key)?.let { return it }
        }
        return null
    }
}
