package com.nexusstream.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YTMusicPagesTest {
    private fun listItem(id: String, title: String, artist: String, artistId: String, album: String = "", albumId: String = "") = """
        {"musicResponsiveListItemRenderer":{
          "playlistItemData":{"videoId":"$id"},
          "flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$title"}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
              {"text":"$artist","navigationEndpoint":{"browseEndpoint":{"browseId":"$artistId"}}}
              ${if (album.isNotEmpty()) """,{"text":" • "},{"text":"$album","navigationEndpoint":{"browseEndpoint":{"browseId":"$albumId"}}}""" else ""}
            ]}}}
          ]}}"""

    @Test
    fun homePageQuickPicksChipsMixesAndContinuation() {
        val root = JSONObject("""
          {"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"selected":true,"content":{"sectionListRenderer":{
            "header":{"chipCloudRenderer":{"chips":[{"chipCloudChipRenderer":{"text":{"runs":[{"text":"Relax"}]},
              "navigationEndpoint":{"browseEndpoint":{"browseId":"FEmusic_home","params":"P1"}}}}]}},
            "contents":[
              {"musicCarouselShelfRenderer":{"numItemsPerColumn":"4",
                "header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Quick picks"}]},"strapline":{"runs":[{"text":"Start radio from a song"}]}}},
                "contents":[${listItem("aaaaaaaaaaa", "One", "Artist A", "UCa", "Album X", "MPREb_x")}, ${listItem("bbbbbbbbbbb", "Two", "Artist B", "UCb")}]}},
              {"musicCarouselShelfRenderer":{
                "header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Mixed for you"}]},
                  "moreContentButton":{"buttonRenderer":{"navigationEndpoint":{"browseEndpoint":{"browseId":"FEmusic_mixed_for_you"}}}}}},
                "contents":[{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"My Supermix"}]},
                  "navigationEndpoint":{"watchPlaylistEndpoint":{"playlistId":"RDTMAK5uy_super"}}}},
                  {"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Album Y"}]},"subtitle":{"runs":[{"text":"Album • Artist"}]},
                  "navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb_y","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ALBUM"}}}}}}]}}
            ],
            "continuations":[{"nextContinuationData":{"continuation":"CONT"}}]
          }}}}]}}}
        """)
        val page = YTMusicPages.parsePage(root)
        assertEquals("CONT", page.continuation)
        assertEquals(listOf("Relax"), page.chips.map { it.title })
        assertEquals("P1", page.chips[0].params)
        val quick = page.sections[0]
        assertEquals("Quick picks", quick.title)
        assertEquals("Start radio from a song", quick.strapline)
        assertEquals(4, quick.rowsPerColumn)
        assertTrue(quick.isSongList)
        val first = quick.cards[0].track!!
        assertEquals("Artist A", first.artistOrChannel)
        assertEquals("MPREb_x", first.albumId)
        assertEquals("https://www.youtube.com/channel/UCa", first.channelUrl)
        val mixes = page.sections[1]
        assertEquals("FEmusic_mixed_for_you", mixes.moreBrowseId)
        assertEquals("VLRDTMAK5uy_super", mixes.cards[0].browseId)
        assertEquals(YTMusicApi.CardKind.ALBUM, mixes.cards[1].kind)
        assertEquals("MPREb_y", mixes.cards[1].browseId)
    }

    @Test
    fun albumPageHeaderAndTracksGetTheCover() {
        val root = JSONObject("""
          {"contents":{"twoColumnBrowseResultsRenderer":{
            "tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[{"musicResponsiveHeaderRenderer":{
              "title":{"runs":[{"text":"Album X"}]},"subtitle":{"runs":[{"text":"Album"},{"text":" • "},{"text":"2024"}]},
              "straplineTextOne":{"runs":[{"text":"Artist A","navigationEndpoint":{"browseEndpoint":{"browseId":"UCa"}}}]},
              "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://img/cover.jpg"}]}}},
              "buttons":[{"musicPlayButtonRenderer":{"playNavigationEndpoint":{"watchEndpoint":{"playlistId":"OLAK5uy_abcdefghijklmnopqrstuvwxyz12345"}}}}]
            }}]}}}}],
            "secondaryContents":{"sectionListRenderer":{"contents":[{"musicShelfRenderer":{"contents":[
              ${listItem("ccccccccccc", "Track 1", "", "")}
            ]}}]}}
          }}}
        """)
        val page = YTMusicPages.albumized(YTMusicPages.parsePage(root), "MPREb_x")
        val header = page.header!!
        assertEquals("Album X", header.title)
        assertEquals("Artist A", header.artistName)
        assertEquals("UCa", header.artistId)
        assertEquals("OLAK5uy_abcdefghijklmnopqrstuvwxyz12345", header.playlistId)
        val track = page.songs.single()
        assertEquals("https://img/cover.jpg", track.artworkUrl)
        assertEquals("Artist A", track.artistOrChannel)
        assertEquals("MPREb_x", track.albumId)
        assertEquals("Album X", track.albumName)
    }

    @Test
    fun artistHeaderAndMoodButtons() {
        val root = JSONObject("""
          {"header":{"musicImmersiveHeaderRenderer":{"title":{"runs":[{"text":"Artist A"}]},
            "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://img/a.jpg"}]}}},
            "subscriptionButton":{"subscribeButtonRenderer":{"channelId":"UCa","subscriberCountText":{"runs":[{"text":"1.2M"}]}}},
            "startRadioButton":{"buttonRenderer":{"navigationEndpoint":{"watchPlaylistEndpoint":{"playlistId":"RDEMradio"}}}}}},
           "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
             {"gridRenderer":{"items":[{"musicNavigationButtonRenderer":{"buttonText":{"runs":[{"text":"Chill"}]},
               "solid":{"leftStripeColor":4294940672},
               "clickCommand":{"browseEndpoint":{"browseId":"FEmusic_moods_and_genres_category","params":"ggMP"}}}}]}}
           ]}}}}]}}}
        """)
        val page = YTMusicPages.parsePage(root)
        val header = page.header!!
        assertTrue(header.isArtist)
        assertEquals("UCa", header.channelId)
        assertEquals("1.2M", header.subscriberText)
        assertEquals("RDEMradio", header.radioPlaylistId)
        val button = page.sections.single().buttons.single()
        assertEquals("Chill", button.title)
        assertEquals("ggMP", button.params)
        assertEquals(4294940672L, button.color)
    }
}
