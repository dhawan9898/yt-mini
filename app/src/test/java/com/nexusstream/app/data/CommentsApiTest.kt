package com.nexusstream.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentsApiTest {
    @Test
    fun parseCountHandlesSuffixesAndCommas() {
        assertEquals(1200, CommentsApi.parseCount("1.2K"))
        assertEquals(3456, CommentsApi.parseCount("3,456 Comments"))
        assertEquals(2_000_000, CommentsApi.parseCount("2M"))
        assertEquals(-1, CommentsApi.parseCount(""))
    }

    @Test
    fun sortChangesTheSectionToken() {
        assertNotEquals(CommentsApi.sectionToken("abc", CommentsApi.Sort.TOP), CommentsApi.sectionToken("abc", CommentsApi.Sort.NEWEST))
    }

    @Test
    fun parsesCommentViewModelsFromMutations() {
        val json = JSONObject(
            """
            {"onResponseReceivedEndpoints":[{"reloadContinuationItemsCommand":{"continuationItems":[
              {"commentsHeaderRenderer":{"countText":{"runs":[{"text":"1,234"},{"text":" Comments"}]},
                "createRenderer":{"commentSimpleboxRenderer":{"submitButton":{"buttonRenderer":{"serviceEndpoint":{"createCommentEndpoint":{"createCommentParams":"CP"}}}}}},
                "sortMenu":{"sortFilterSubMenuRenderer":{"subMenuItems":[
                  {"serviceEndpoint":{"continuationCommand":{"token":"TOP"}}},
                  {"serviceEndpoint":{"continuationCommand":{"token":"NEW"}}}]}}}},
              {"commentThreadRenderer":{"commentViewModel":{"commentViewModel":{"commentId":"c1","commentKey":"k1","toolbarStateKey":"s1","toolbarSurfaceKey":"t1"}},
                "replies":{"commentRepliesRenderer":{"contents":[{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"R1"}}}}]}}}},
              {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"NEXT"}}}}
            ]}}],
             "frameworkUpdates":{"entityBatchUpdate":{"mutations":[
               {"entityKey":"k1","payload":{"commentEntityPayload":{"properties":{"content":{"content":"Great video"},"publishedTime":"2 days ago"},
                 "author":{"displayName":"@someone","channelId":"UCx","isCreator":true},
                 "toolbar":{"likeCountNotliked":"12","replyCount":"3"}}}},
               {"entityKey":"s1","payload":{"engagementToolbarStateEntityPayload":{"likeState":"TOOLBAR_LIKE_STATE_LIKED","heartState":"TOOLBAR_HEART_STATE_UNHEARTED"}}},
               {"entityKey":"t1","payload":{"engagementToolbarSurfaceEntityPayload":{
                 "likeCommand":{"innertubeCommand":{"performCommentActionEndpoint":{"action":"LIKE"}}},
                 "unlikeCommand":{"innertubeCommand":{"performCommentActionEndpoint":{"action":"UNLIKE"}}},
                 "replyCommand":{"innertubeCommand":{"createCommentReplyEndpoint":{"createReplyParams":"RP"}}}}}}
             ]}}}
            """
        )
        val r = CommentsApi.parse(json, "vid")
        assertEquals(1234, r.totalCount)
        assertEquals("CP", r.createParams)
        assertEquals("TOP", r.sortTokens[CommentsApi.Sort.TOP])
        assertEquals("NEW", r.sortTokens[CommentsApi.Sort.NEWEST])
        assertEquals("NEXT", r.continuation)
        val c = r.comments.single()
        assertEquals("Great video", c.text)
        assertEquals("@someone", c.author)
        assertEquals(12, c.likes)
        assertEquals(3, c.replyCount)
        assertTrue(c.isLiked)
        assertFalse(c.isHearted)
        assertTrue(c.authorIsCreator)
        assertEquals("LIKE", c.likeAction)
        assertEquals("UNLIKE", c.unlikeAction)
        assertEquals("RP", c.replyParams)
        assertTrue(c.loadReplies != null)
    }
}
