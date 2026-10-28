package com.nexusstream.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Comments straight from YouTube's web API, so the signed-in account can post, reply and like,
 * and the list can be sorted Top / Newest. Each comment arrives as a `commentViewModel` whose
 * text, author, counts and action commands sit in `frameworkUpdates` mutations, keyed by id.
 */
object CommentsApi {
    enum class Sort { TOP, NEWEST }

    /** One page of comments (or replies) plus what the comments header offers. */
    class Result(
        val comments: List<CommentModel>,
        val continuation: String?,
        val totalCount: Int,
        val createParams: String?,
        /** The header's sort menu: continuation per sort order. */
        val sortTokens: Map<Sort, String>
    )

    /** The first page of a video's comments in the given order. */
    fun load(videoId: String, sort: Sort): Result {
        val token = sectionToken(videoId, sort)
        return parse(InnerTube.web("next", JSONObject().put("continuation", token)), videoId)
    }

    fun more(continuation: String, videoId: String): Result =
        parse(InnerTube.web("next", JSONObject().put("continuation", continuation)), videoId)

    /** `GetCommentsSectionParams`, as YouTube.js encodes it. */
    internal fun sectionToken(videoId: String, sort: Sort): String = Proto()
        .message(2) { string(2, videoId) }
        .int(3, 6)
        .message(6) {
            message(4) {
                string(4, videoId)
                if (sort == Sort.NEWEST) int(6, 1)
                int(15, 2)
                string(16, "")
            }
            string(8, "comments-section")
        }
        .base64Url()

    /** `CreateCommentParams` for a top-level comment when the header gives none. */
    internal fun createParams(videoId: String): String =
        Proto().string(2, videoId).message(5) {}.int(10, 7).base64Url()

    fun post(createParams: String, text: String) {
        check(GoogleAccount.isSignedIn()) { "Sign in to comment" }
        val res = InnerTube.web("comment/create_comment", JSONObject().put("createCommentParams", createParams).put("commentText", text))
        checkStatus(res)
    }

    fun reply(replyParams: String, text: String) {
        check(GoogleAccount.isSignedIn()) { "Sign in to reply" }
        val res = InnerTube.web("comment/create_comment_reply", JSONObject().put("createReplyParams", replyParams).put("commentText", text))
        checkStatus(res)
    }

    /** Sends a like / unlike (the action string comes from the comment's toolbar). */
    fun act(action: String) {
        check(GoogleAccount.isSignedIn()) { "Sign in to like comments" }
        InnerTube.web("comment/perform_comment_action", JSONObject().put("actions", JSONArray().put(action)))
    }

    private fun checkStatus(res: JSONObject) {
        val actions = res.optJSONArray("actions")?.toString().orEmpty()
        check(!actions.contains("\"STATUS_FAILED\"")) { "YouTube didn't accept the comment" }
    }

    internal fun parse(root: JSONObject, videoId: String): Result {
        val items = JSONArray()
        root.optJSONArray("onResponseReceivedEndpoints")?.let { endpoints ->
            for (i in 0 until endpoints.length()) {
                val e = endpoints.optJSONObject(i) ?: continue
                val list = (e.optJSONObject("reloadContinuationItemsCommand") ?: e.optJSONObject("appendContinuationItemsAction"))
                    ?.optJSONArray("continuationItems") ?: continue
                for (j in 0 until list.length()) items.put(list.opt(j))
            }
        }
        val entities = HashMap<String, JSONObject>()
        root.optJSONObject("frameworkUpdates")?.optJSONObject("entityBatchUpdate")?.optJSONArray("mutations")?.let { m ->
            for (i in 0 until m.length()) {
                val mutation = m.optJSONObject(i) ?: continue
                val key = mutation.optString("entityKey")
                val payload = mutation.optJSONObject("payload") ?: continue
                if (key.isNotEmpty()) entities[key] = payload
            }
        }

        val comments = ArrayList<CommentModel>()
        var continuation: String? = null
        var total = -1
        var createParams: String? = null
        val sortTokens = HashMap<Sort, String>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            item.optJSONObject("commentsHeaderRenderer")?.let { header ->
                total = parseCount(InnerTube.text(header.optJSONObject("countText")))
                createParams = find(header, "createCommentEndpoint")?.optString("createCommentParams")?.takeIf { it.isNotEmpty() }
                header.optJSONObject("sortMenu")?.optJSONObject("sortFilterSubMenuRenderer")?.optJSONArray("subMenuItems")?.let { subs ->
                    for (k in 0 until subs.length()) {
                        val sub = subs.optJSONObject(k) ?: continue
                        val token = find(sub, "continuationCommand")?.optString("token") ?: continue
                        sortTokens[if (k == 0) Sort.TOP else Sort.NEWEST] = token
                    }
                }
            }
            item.optJSONObject("commentThreadRenderer")?.let { thread ->
                val view = thread.optJSONObject("commentViewModel")?.optJSONObject("commentViewModel") ?: return@let
                val repliesToken = thread.optJSONObject("replies")?.let { find(it, "continuationCommand")?.optString("token") }
                comment(view, entities, videoId, repliesToken)?.let(comments::add)
            }
            // Reply pages list the view models directly.
            item.optJSONObject("commentViewModel")?.let { view -> comment(view, entities, videoId, null)?.let(comments::add) }
            item.optJSONObject("continuationItemRenderer")?.let { c ->
                find(c, "continuationCommand")?.optString("token")?.takeIf { it.isNotEmpty() }?.let { continuation = it }
            }
        }
        return Result(comments, continuation, total, createParams, sortTokens)
    }

    private fun comment(view: JSONObject, entities: Map<String, JSONObject>, videoId: String, repliesToken: String?): CommentModel? {
        val entity = entities[view.optString("commentKey")]?.optJSONObject("commentEntityPayload") ?: return null
        val state = entities[view.optString("toolbarStateKey")]?.optJSONObject("engagementToolbarStateEntityPayload")
        val surface = entities[view.optString("toolbarSurfaceKey")]?.optJSONObject("engagementToolbarSurfaceEntityPayload")
        val props = entity.optJSONObject("properties") ?: JSONObject()
        val author = entity.optJSONObject("author") ?: JSONObject()
        val toolbar = entity.optJSONObject("toolbar") ?: JSONObject()
        val avatar = entity.optJSONObject("avatar")?.optJSONObject("image")?.optJSONArray("sources")
            ?.let { it.optJSONObject(it.length() - 1)?.optString("url") }
            ?: author.optString("avatarThumbnailUrl")
        val replyCount = parseCount(toolbar.optString("replyCount"))
        val token = repliesToken
        return CommentModel(
            author = author.optString("displayName"),
            avatarUrl = avatar.orEmpty(),
            text = props.optJSONObject("content")?.optString("content").orEmpty(),
            likes = parseCount(toolbar.optString("likeCountNotliked")).coerceAtLeast(0),
            date = props.optString("publishedTime"),
            pinned = view.has("pinnedText"),
            replyCount = replyCount.coerceAtLeast(0),
            loadReplies = token?.let { t -> suspend { kotlin.runCatching { more(t, videoId).comments } } },
            id = view.optString("commentId"),
            isLiked = state?.optString("likeState") == "TOOLBAR_LIKE_STATE_LIKED",
            isHearted = state?.optString("heartState") == "TOOLBAR_HEART_STATE_HEARTED",
            likeAction = surface?.optJSONObject("likeCommand")?.let { find(it, "performCommentActionEndpoint") }?.optString("action")?.takeIf { it.isNotEmpty() },
            unlikeAction = surface?.optJSONObject("unlikeCommand")?.let { find(it, "performCommentActionEndpoint") }?.optString("action")?.takeIf { it.isNotEmpty() },
            replyParams = surface?.optJSONObject("replyCommand")?.let { find(it, "createCommentReplyEndpoint") }?.optString("createReplyParams")?.takeIf { it.isNotEmpty() },
            authorIsCreator = author.optBoolean("isCreator"),
            authorChannelId = author.optString("channelId")
        )
    }

    /** "1.2K" / "3,456" / "12 Comments" as a number (-1 when there is none). */
    internal fun parseCount(text: String): Int {
        val m = Regex("""([\d.,]+)\s*([KMB])?""", RegexOption.IGNORE_CASE).find(text) ?: return -1
        val raw = m.groupValues[1].replace(",", "")
        val base = raw.toDoubleOrNull() ?: return -1
        val mult = when (m.groupValues[2].uppercase()) { "K" -> 1_000.0; "M" -> 1_000_000.0; "B" -> 1_000_000_000.0; else -> 1.0 }
        return (base * mult).toInt()
    }

    /** First object under [key] anywhere below [node]. */
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
