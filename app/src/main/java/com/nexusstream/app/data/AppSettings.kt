package com.nexusstream.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Country / language of the content YouTube returns (trending, search ranking...). */
object ContentRegions {
    /** Country code to display name, in the order shown in settings. */
    val countries: List<Pair<String, String>> = listOf(
        "US" to "United States", "GB" to "United Kingdom", "IN" to "India", "CA" to "Canada",
        "AU" to "Australia", "DE" to "Germany", "FR" to "France", "ES" to "Spain", "IT" to "Italy",
        "BR" to "Brazil", "MX" to "Mexico", "JP" to "Japan", "KR" to "South Korea",
        "ID" to "Indonesia", "TR" to "Turkey"
    )

    private val languages = mapOf(
        "DE" to "de", "FR" to "fr", "ES" to "es", "MX" to "es", "IT" to "it", "BR" to "pt",
        "JP" to "ja", "KR" to "ko", "ID" to "id", "TR" to "tr"
    )

    /** Interface language YouTube should use for a country (English where unsure). */
    fun languageFor(country: String): String = languages[country] ?: "en"

    fun nameOf(country: String): String = countries.firstOrNull { it.first == country }?.second ?: country
}

/**
 * User preferences from the Settings screen, persisted in SharedPreferences.
 * Call [init] once (the activity does; workers and the service call it too - it is idempotent).
 */
object AppSettings {
    private const val PREFS = "nexus_settings"
    private var prefs: android.content.SharedPreferences? = null

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** Preferred video height (e.g. 720); 0 = automatic (up to 720p). */
    private val _defaultVideoHeight = MutableStateFlow(0)
    val defaultVideoHeight: StateFlow<Int> = _defaultVideoHeight.asStateFlow()

    /** Caps video at 480p and audio at 96 kbps. */
    private val _dataSaver = MutableStateFlow(false)
    val dataSaver: StateFlow<Boolean> = _dataSaver.asStateFlow()

    private val _autoplay = MutableStateFlow(true)
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()

    private val _captionsByDefault = MutableStateFlow(false)
    val captionsByDefault: StateFlow<Boolean> = _captionsByDefault.asStateFlow()

    private val _wifiOnlyDownloads = MutableStateFlow(false)
    val wifiOnlyDownloads: StateFlow<Boolean> = _wifiOnlyDownloads.asStateFlow()

    /** Show dislike counts from the Return YouTube Dislike service (off: no request is made). */
    private val _showDislikeCounts = MutableStateFlow(false)
    val showDislikeCounts: StateFlow<Boolean> = _showDislikeCounts.asStateFlow()

    private val _contentCountry = MutableStateFlow("US")
    val contentCountry: StateFlow<String> = _contentCountry.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _themeMode.value = runCatching { ThemeMode.valueOf(p.getString("theme", "SYSTEM") ?: "SYSTEM") }
            .getOrDefault(ThemeMode.SYSTEM)
        _defaultVideoHeight.value = p.getInt("default_height", 0)
        _dataSaver.value = p.getBoolean("data_saver", false)
        _autoplay.value = p.getBoolean("autoplay", true)
        _captionsByDefault.value = p.getBoolean("captions_default", false)
        _wifiOnlyDownloads.value = p.getBoolean("wifi_only_downloads", false)
        _contentCountry.value = p.getString("country", "US") ?: "US"
        _showDislikeCounts.value = p.getBoolean("dislike_counts", false)
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs?.edit()?.putString("theme", mode.name)?.apply()
    }

    fun setDefaultVideoHeight(height: Int) {
        _defaultVideoHeight.value = height
        prefs?.edit()?.putInt("default_height", height)?.apply()
    }

    fun setDataSaver(enabled: Boolean) {
        _dataSaver.value = enabled
        prefs?.edit()?.putBoolean("data_saver", enabled)?.apply()
    }

    fun setAutoplay(enabled: Boolean) {
        _autoplay.value = enabled
        prefs?.edit()?.putBoolean("autoplay", enabled)?.apply()
    }

    fun setCaptionsByDefault(enabled: Boolean) {
        _captionsByDefault.value = enabled
        prefs?.edit()?.putBoolean("captions_default", enabled)?.apply()
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        _wifiOnlyDownloads.value = enabled
        prefs?.edit()?.putBoolean("wifi_only_downloads", enabled)?.apply()
    }

    fun setContentCountry(country: String) {
        _contentCountry.value = country
        prefs?.edit()?.putString("country", country)?.apply()
    }

    fun setShowDislikeCounts(enabled: Boolean) {
        _showDislikeCounts.value = enabled
        prefs?.edit()?.putBoolean("dislike_counts", enabled)?.apply()
    }

    /** All settings as JSON, for backups. */
    fun exportJson(): org.json.JSONObject = org.json.JSONObject()
        .put("theme", _themeMode.value.name)
        .put("defaultHeight", _defaultVideoHeight.value)
        .put("dataSaver", _dataSaver.value)
        .put("autoplay", _autoplay.value)
        .put("captions", _captionsByDefault.value)
        .put("wifiOnly", _wifiOnlyDownloads.value)
        .put("country", _contentCountry.value)
        .put("dislikeCounts", _showDislikeCounts.value)

    /** Applies settings from a backup; missing or unknown values are left as they are. */
    fun importJson(json: org.json.JSONObject) {
        runCatching { ThemeMode.valueOf(json.optString("theme")) }.getOrNull()?.let { setThemeMode(it) }
        if (json.has("defaultHeight")) setDefaultVideoHeight(json.optInt("defaultHeight"))
        if (json.has("dataSaver")) setDataSaver(json.optBoolean("dataSaver"))
        if (json.has("autoplay")) setAutoplay(json.optBoolean("autoplay"))
        if (json.has("captions")) setCaptionsByDefault(json.optBoolean("captions"))
        if (json.has("wifiOnly")) setWifiOnlyDownloads(json.optBoolean("wifiOnly"))
        json.optString("country").takeIf { c -> ContentRegions.countries.any { it.first == c } }?.let { setContentCountry(it) }
        if (json.has("dislikeCounts")) setShowDislikeCounts(json.optBoolean("dislikeCounts"))
    }
}
