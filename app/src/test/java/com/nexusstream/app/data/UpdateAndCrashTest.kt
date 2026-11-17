package com.nexusstream.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateAndCrashTest {

    @Test
    fun versionNumberIsTheLastNumber() {
        assertEquals(37, UpdateChecker.versionNumber("1.0.37"))
        assertEquals(37, UpdateChecker.versionNumber("v1.0.37"))
        assertEquals(120, UpdateChecker.versionNumber(" 1.0.120 "))
        assertNull(UpdateChecker.versionNumber("latest"))
    }

    @Test
    fun newerMeansHigherBuildNumber() {
        assertTrue(UpdateChecker.isNewer("1.0.37", "v1.0.38"))
        assertFalse(UpdateChecker.isNewer("1.0.37", "v1.0.37"))
        assertFalse(UpdateChecker.isNewer("1.0.37", "v1.0.12"))
        // A new series: the last number alone would call 2.2.0 older than 2.1.15.
        assertTrue(UpdateChecker.isNewer("2.1.15", "v2.2.0"))
        assertFalse(UpdateChecker.isNewer("2.2.0", "v2.1.15"))
        assertTrue(UpdateChecker.isNewer("unknown", "v1.0.1"))
        assertFalse(UpdateChecker.isNewer("2.1.15", "latest"))
    }

    @Test
    fun parsesLatestReleaseAndPicksTheApkAsset() {
        val json = """
            {"tag_name":"v1.0.42","html_url":"https://github.com/x/y/releases/tag/v1.0.42",
             "assets":[
               {"name":"notes.txt","browser_download_url":"https://example.com/notes.txt"},
               {"name":"NexusStream-v1.0.42.apk","size":31000000,"browser_download_url":"https://example.com/NexusStream-v1.0.42.apk"}
             ]}
        """.trimIndent()
        val release = UpdateChecker.parseLatest(json)!!
        assertEquals(42, release.version)
        assertEquals("https://example.com/NexusStream-v1.0.42.apk", release.downloadUrl)
        assertEquals(31_000_000L, release.sizeBytes)
        assertEquals("https://github.com/x/y/releases/tag/v1.0.42", release.pageUrl)
    }

    @Test
    fun badReleaseJsonGivesNull() {
        assertNull(UpdateChecker.parseLatest("not json"))
        assertNull(UpdateChecker.parseLatest("""{"message":"Not Found"}"""))
    }

    @Test
    fun crashReportContainsTheEssentials() {
        val report = CrashReporter.format("1.0.5", "Pixel (Android 15)", "main", IllegalStateException("boom"), 0L)
        assertTrue(report.contains("Version: 1.0.5"))
        assertTrue(report.contains("Device: Pixel (Android 15)"))
        assertTrue(report.contains("IllegalStateException: boom"))
    }

    @Test
    fun contentRegionsMapToLanguages() {
        assertEquals("de", ContentRegions.languageFor("DE"))
        assertEquals("en", ContentRegions.languageFor("US"))
        assertEquals("en", ContentRegions.languageFor("ZZ"))
        assertEquals("India", ContentRegions.nameOf("IN"))
        assertEquals("ZZ", ContentRegions.nameOf("ZZ"))
    }
}
