package com.nexusstream.app.data

import com.nexusstream.app.model.MediaContentType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YTMusicParsingTest {

    private fun runs(vararg text: String) = """{"runs":[${text.joinToString(",") { """{"text":"$it"}""" }}]}"""

    private val next = JSONObject("""
        {"contents":{"singleColumnMusicWatchNextResultsRenderer":{"tabbedRenderer":{"watchNextTabbedResultsRenderer":{"tabs":[
          {"tabRenderer":{"title":"Up next","content":{"musicQueueRenderer":{"content":{"playlistPanelRenderer":{
            "contents":[
              {"playlistPanelVideoRenderer":{"videoId":"aaaaaaaaaaa","title":${runs("Song A")},
                "shortBylineText":${runs("Artist A")},"lengthText":${runs("3:21")},
                "thumbnail":{"thumbnails":[{"url":"https://img/a-small"},{"url":"https://img/a-big"}]}}},
              {"playlistPanelVideoWrapperRenderer":{"primaryRenderer":{"playlistPanelVideoRenderer":{
                "videoId":"bbbbbbbbbbb","title":${runs("Song B")},"shortBylineText":${runs("Artist B")},
                "lengthText":${runs("4:05")}}}}}
            ],
            "continuations":[{"nextRadioContinuationData":{"continuation":"RADIO_TOKEN"}}]
          }}}}}},
          {"tabRenderer":{"title":"Lyrics","endpoint":{"browseEndpoint":{"browseId":"MPLYt_lyrics"}}}},
          {"tabRenderer":{"title":"Related","endpoint":{"browseEndpoint":{"browseId":"MPTRt_related"}}}}
        ]}}}}}
    """)

    @Test
    fun watchGivesQueueContinuationAndTabIds() {
        val info = YTMusicApi.parseWatch(next)
        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), info.queue.map { it.id })
        assertEquals("Artist A", info.queue[0].artistOrChannel)
        assertEquals(201_000L, info.queue[0].durationMs)
        assertEquals("https://img/a-big", info.queue[0].artworkUrl)
        assertTrue(info.queue.all { it.contentType == MediaContentType.MUSIC_TRACK })
        assertEquals("RADIO_TOKEN", info.continuation)
        assertEquals("MPLYt_lyrics", info.lyricsBrowseId)
        assertEquals("MPTRt_related", info.relatedBrowseId)
    }

    @Test
    fun watchWithoutLyricsTabHasNoLyricsId() {
        val info = YTMusicApi.parseWatch(JSONObject("""
            {"contents":{"singleColumnMusicWatchNextResultsRenderer":{"tabbedRenderer":{"watchNextTabbedResultsRenderer":{"tabs":[
              {"tabRenderer":{"content":{}}},
              {"tabRenderer":{"unselectable":true}},
              {"tabRenderer":{"endpoint":{"browseEndpoint":{"browseId":"MPTRt_x"}}}}
            ]}}}}}
        """))
        assertTrue(info.queue.isEmpty())
        assertNull(info.continuation)
        assertNull(info.lyricsBrowseId)
        assertEquals("MPTRt_x", info.relatedBrowseId)
    }

    @Test
    fun radioContinuationPage() {
        val page = YTMusicApi.parseQueueContinuation(JSONObject("""
            {"continuationContents":{"playlistPanelContinuation":{
              "contents":[{"playlistPanelVideoRenderer":{"videoId":"ccccccccccc","title":${runs("Song C")},"lengthText":${runs("2:00")}}}],
              "continuations":[{"nextRadioContinuationData":{"continuation":"NEXT2"}}]}}}
        """))
        assertEquals(listOf("ccccccccccc"), page.items.map { it.id })
        assertEquals("NEXT2", page.continuation)
    }

    @Test
    fun plainLyricsWithSource() {
        val lyrics = YTMusicApi.parseLyrics(JSONObject("""
            {"contents":{"sectionListRenderer":{"contents":[{"musicDescriptionShelfRenderer":{
              "description":${runs("Line one\\nLine two")},"footer":${runs("Source: LyricFind")}}}]}}}
        """))!!
        assertEquals("Line one\nLine two", lyrics.text)
        assertEquals("Source: LyricFind", lyrics.source)
        assertNull(YTMusicApi.parseLyrics(JSONObject("""{"contents":{"sectionListRenderer":{"contents":[]}}}""")))
    }

    @Test
    fun relatedShelves() {
        fun browse(id: String, type: String) =
            """{"browseEndpoint":{"browseId":"$id","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"$type"}}}}"""
        val thumb = """{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://img/t"}]}}}"""
        val sections = YTMusicApi.parseRelated(JSONObject("""
            {"contents":{"sectionListRenderer":{"contents":[
              {"musicCarouselShelfRenderer":{
                "header":{"musicCarouselShelfBasicHeaderRenderer":{"title":${runs("You might also like")}}},
                "contents":[{"musicResponsiveListItemRenderer":{
                  "thumbnail":$thumb,
                  "playlistItemData":{"videoId":"ddddddddddd"},
                  "flexColumns":[
                    {"musicResponsiveListItemFlexColumnRenderer":{"text":${runs("Song D")}}},
                    {"musicResponsiveListItemFlexColumnRenderer":{"text":${runs("Artist D", " • ", "Album D")}}}
                  ]}}]}},
              {"musicCarouselShelfRenderer":{
                "header":{"musicCarouselShelfBasicHeaderRenderer":{"title":${runs("Recommended playlists")}}},
                "contents":[
                  {"musicTwoRowItemRenderer":{"title":${runs("Chill Mix")},"subtitle":${runs("Playlist")},
                    "thumbnailRenderer":$thumb,"navigationEndpoint":${browse("VLPL123", "MUSIC_PAGE_TYPE_PLAYLIST")}}},
                  {"musicTwoRowItemRenderer":{"title":${runs("Album E")},"subtitle":${runs("Album", " • ", "2020")},
                    "thumbnailRenderer":$thumb,"navigationEndpoint":${browse("MPREb_e", "MUSIC_PAGE_TYPE_ALBUM")},
                    "thumbnailOverlay":{"musicItemThumbnailOverlayRenderer":{"content":{"musicPlayButtonRenderer":{
                      "playNavigationEndpoint":{"watchPlaylistEndpoint":{"playlistId":"OLAK5uy_e"}}}}}}}},
                  {"musicTwoRowItemRenderer":{"title":${runs("Album without playlist")},
                    "navigationEndpoint":${browse("MPREb_f", "MUSIC_PAGE_TYPE_ALBUM")}}}
                ]}},
              {"musicCarouselShelfRenderer":{
                "header":{"musicCarouselShelfBasicHeaderRenderer":{"title":${runs("Similar artists")}}},
                "contents":[{"musicTwoRowItemRenderer":{"title":${runs("Artist F")},"subtitle":${runs("1M subscribers")},
                  "thumbnailRenderer":$thumb,"navigationEndpoint":${browse("UCartist", "MUSIC_PAGE_TYPE_ARTIST")}}}]}},
              {"musicDescriptionShelfRenderer":{"header":${runs("About the artist")},"description":${runs("Bio text")}}}
            ]}}}
        """))
        assertEquals(listOf("You might also like", "Recommended playlists", "Similar artists", "About the artist"), sections.map { it.title })

        val song = sections[0].cards.single()
        assertTrue(sections[0].isSongList)
        assertEquals(YTMusicApi.CardKind.SONG, song.kind)
        assertEquals("ddddddddddd", song.track?.id)
        assertEquals("Artist D", song.track?.artistOrChannel)
        assertEquals("Artist D • Album D", song.subtitle)

        val tiles = sections[1].cards
        assertEquals(false, sections[1].isSongList)
        assertEquals(listOf(YTMusicApi.CardKind.PLAYLIST, YTMusicApi.CardKind.ALBUM), tiles.map { it.kind })
        assertEquals("https://www.youtube.com/playlist?list=PL123", tiles[0].url)
        assertEquals("https://www.youtube.com/playlist?list=OLAK5uy_e", tiles[1].url)

        val artist = sections[2].cards.single()
        assertEquals(YTMusicApi.CardKind.ARTIST, artist.kind)
        assertEquals("https://www.youtube.com/channel/UCartist", artist.url)

        assertEquals("Bio text", sections[3].description)
    }

    @Test
    fun panelTrackKeepsAlbumArtistAndCredits() {
        val renderer = JSONObject("""
            {"videoId":"ddddddddddd","title":${runs("Song D")},"lengthText":${runs("3:00")},
             "longBylineText":{"runs":[
               {"text":"Artist D","navigationEndpoint":{"browseEndpoint":{"browseId":"UCartistD"}}},
               {"text":" • "},
               {"text":"Album D","navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb_albumD"}}},
               {"text":" • "},{"text":"2021"}]},
             "menu":{"menuRenderer":{"items":[
               {"menuNavigationItemRenderer":{"navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb_albumD"}}}},
               {"menuNavigationItemRenderer":{"navigationEndpoint":{"browseEndpoint":{"browseId":"MPTCcredits"}}}}]}}}
        """)
        val track = YTMusicApi.panelTrack(renderer)!!
        assertEquals("Artist D", track.artistOrChannel)
        assertEquals("https://www.youtube.com/channel/UCartistD", track.channelUrl)
        assertEquals("MPREb_albumD", track.albumId)
        assertEquals("Album D", track.albumName)
        assertEquals("MPTCcredits", YTMusicApi.trackInfo(renderer).creditsBrowseId)
    }

    @Test
    fun creditsSections() {
        val credits = YTMusicApi.parseCredits(JSONObject("""
            {"onResponseReceivedActions":[{"openPopupAction":{"popup":{"dismissableDialogRenderer":{"sections":[
              {"dismissableDialogContentSectionRenderer":{"title":${runs("Performed by")},"subtitle":${runs("A", ", ", "B")}}},
              {"dismissableDialogContentSectionRenderer":{"title":${runs("Written by")},"subtitle":${runs("C")}}}
            ]}}}}]}
        """))
        assertEquals(listOf("Performed by", "Written by"), credits.map { it.title })
        assertEquals(listOf("A", "B"), credits[0].names)
    }
}
