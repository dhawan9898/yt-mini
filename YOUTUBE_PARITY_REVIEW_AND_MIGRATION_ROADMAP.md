# yt-mini vs. the official YouTube and YouTube Music apps

A review of how this app (one APK for both) behaves next to the two official Android apps. "Same"
means it works the way the official app does; "Different" is a deliberate choice; "Missing" is not
in this app.

## How the two apps are combined

| | Official | yt-mini |
|---|---|---|
| Apps | Two separate apps, two icons | One app; the logo at the top switches YouTube ↔ YT Music (like YouTube's own Premium logo menu) |
| State when switching | Each app keeps its own tabs and scroll | Same: each side keeps its tab, topic chip and scroll position |
| Player | One per app; starting music in YTM stops the YouTube video | One shared player: starting either replaces what was playing |
| Watch history | YTM plays stay out of YouTube history | Same |

## YouTube

| Area | Status | Notes |
|---|---|---|
| Bottom bar | Different | Home, Shorts, Subscriptions, You. No centre **Create (+)** button: uploading lives in **You → Upload a video** (signed in) |
| Home feed, topic chips, Explore destinations | Same | Signed in: the account's own chips; signed out: general topics. Games are left out on purpose |
| Pull to refresh | Same | A failed refresh keeps the list and says why |
| Shorts | Same | Vertical pager, precached next shorts, comments, loop, retry on failure |
| Subscriptions | Same | Feed, channel row, "All" page |
| You tab | Same | History, Playlists, Downloads, Watch later, Liked videos, clips, movies, time watched |
| Watch page | Same | Description, chapters, comments (sort, reply, like), Save, Download, Share, Cast, live chat, transcript |
| Player gestures | Same | Double-tap seek, swipe down to minimise, swipe up for fullscreen, lock, speed, quality, captions |
| Seek-bar extras | Same | Storyboard preview, "Most replayed" heat map, Jump ahead, chapter gaps |
| End screens and info cards | Same | |
| Playback errors | Same | Message over the video with **Retry**, which continues from where it stopped (it used to be a red line with no retry) |
| Playlists, Watch later, Liked videos | Same | Tapping a video plays the list in order from there; **Play all / Shuffle** queue the whole list (they used to play one video) |
| Single videos elsewhere | Same | Plays alone, then autoplays related videos |
| Mini player | Different | A floating picture-in-picture window you can move and pinch-resize, instead of the bar above the tabs. Audio-only playback uses a bar |
| Background play, PiP, downloads, no ads | Different | Premium features are always on |
| Dislike counts, SponsorBlock | Different | Extras not in the official app (both can be switched off) |
| "Remind me to take a break" | Same | Counts time spent watching, so a short pause no longer starts it over |
| Notifications, bedtime reminder, Restricted mode, Incognito, pause history | Same | |
| Android Auto, widgets, app shortcuts, open YouTube links | Same | |
| Recording Shorts, going live, Super Chat / memberships / purchases | Missing | |
| YouTube Kids / supervised accounts | Missing | |

## YouTube Music

| Area | Status | Notes |
|---|---|---|
| Bottom bar | Different | Home, Explore, Library. No **Samples** tab |
| Home moods and shelves | Same | |
| Explore (new releases, charts, moods, podcasts) | Same | |
| Library | Same | Account tabs, History, Recap (signed in); Downloads and On this device for everyone |
| Now playing | Same | Up next, Lyrics (time-synced, karaoke), Related, song menu, credits, video/audio switch |
| Radio / endless queue | Same | Up next refills from the radio when it runs low |
| Album, artist and playlist pages | Same | Shuffle and play, "Go to album / artist" |
| Search | Same | Songs, videos, albums, artists, playlists, podcasts and episodes filters |
| Playback errors | Same | Message with **Retry** under the transport controls |
| Samples, Your Mix customisation, Listen together, Speed dial | Missing | |

## Data sources (why some things behave differently)

The official Data API v3 is tried first, with scraping (NewPipeExtractor / InnerTube) as the guarded
fallback; see the README for the quota rules. Streams, YT Music, Shorts and signed-in feeds always
come from InnerTube, so they keep working when the API quota is spent. When YouTube changes its
site, those are the parts that can break first; errors say so in plain words with a way to retry.

## Next

- A Create (+) entry in the bottom bar, if the upload page should be one tap away.
- A Samples-style vertical music feed for YT Music.
- UI screenshot tests (Paparazzi works with this project's AGP version).
- Restrict the API key in Google Cloud to this package and signing SHA-1 (see the README).
