# yt-mini (NexusStream)

An ad-free Android client for YouTube and YouTube Music (Kotlin, Jetpack Compose, Media3).

## Where the data comes from

The app runs on the **official YouTube Data API v3**. Scraping is the guarded fallback.

| What | Primary | Fallback (scraping) |
|---|---|---|
| Home feed (All and topic chips) | Official most-popular chart (1 unit) | Search for "trending" |
| Search (videos, channels, playlists) | API search (100 units) while the day's search budget lasts | NewPipeExtractor search |
| Video page (title, likes, views, description, tags, subscribers) | API (2 units) | NewPipeExtractor |
| Channel and playlist pages | API (about 3 units) | NewPipeExtractor |
| Comments (signed out) | API (1 unit) | InnerTube, then NewPipeExtractor |
| Related videos, seek-bar storyboard, chapter list | Extractor (the API has none of these); "more from this channel" via the API if the extractor is late | - |
| Playback streams, search suggestions, YT Music, Shorts, signed-in feeds, comment actions | Extractor / InnerTube only (no API equivalent) | - |

The API is used first; if it is switched off, its budget for the day is spent, or a call fails, the same screen is served by scraping instead. If scraping fails too, you see its error.
Signed-in users keep the site's own comment API first, because it is the one that can like, reply and post (the official API is read-only without OAuth).

### Quota

The Data API allows 10,000 units a day (resets at midnight Pacific time). The app keeps a persisted daily count (`QuotaTracker`) and stops at 9,000. Expensive calls (search, 100 units) may only use the first 6,000, which is about 60 searches; after that searches go to scraping while the 1-unit calls keep using the API for the rest of the day. Identical requests are cached (15 minutes; 30 for searches), so reopening a screen costs nothing.

## Building

The key is not in the repository. Provide it one of three ways:

```bash
./gradlew assembleRelease -PytApiKey=YOUR_KEY          # command line
export YT_API_KEY=YOUR_KEY                             # environment (CI uses the YT_API_KEY secret)
echo "yt.api.key=YOUR_KEY" >> local.properties         # git-ignored file
```

Without a key the build works and the official-API fallback is simply off.

### Protect the key

An API key inside an APK can be copied. In the Google Cloud console, restrict it to **YouTube Data API v3** and to **Android apps** with package `com.nexusstream.app` and the SHA-1 of `keystore/nexusstream.p12`. The app sends the matching `X-Android-Package` / `X-Android-Cert` headers, so a restricted key keeps working.

```bash
keytool -list -v -keystore keystore/nexusstream.p12 -storetype pkcs12 -storepass nexusstream | grep SHA1
```

## Tests

`./gradlew testDebugUnitTest`. `YouTubeDataApiTest` runs the API client against a local mock server (quota, caching, retries, error handling); `YouTubeApiFallbackTest` covers the mapping into the app's models.

## Web prototype (`src/`, `web/`, `index.html`)

A Vite + React mock-up of the app's look, running on sample data in `src/data/catalog.ts`. It is a design
prototype only: it does not talk to YouTube and is not part of the Android build. Run it with
`npm install && npm run dev`. The Android app in `app/` is the product.
