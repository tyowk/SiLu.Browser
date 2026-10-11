package id.silu.browser
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class Engine(val id: String, val name: String, val search: String, val suggest: String?)

val ENGINES = listOf(Engine("google", "Google", "https://www.google.com/search?q=%s", "https://suggestqueries.google.com/complete/search?client=firefox&q=%s"), Engine("bing", "Bing (Microsoft Edge default)", "https://www.bing.com/search?q=%s", "https://api.bing.com/osjson.aspx?query=%s"), Engine("yahoo", "Yahoo", "https://search.yahoo.com/search?p=%s", null), Engine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com/ac/?type=list&q=%s"), Engine("brave", "Brave Search", "https://search.brave.com/search?q=%s", "https://search.brave.com/api/suggest?q=%s"), Engine("ecosia", "Ecosia", "https://www.ecosia.org/search?q=%s", "https://ac.ecosia.org/autocomplete?q=%s&type=list"), Engine("startpage", "Startpage", "https://www.startpage.com/do/search?q=%s", null), Engine("yandex", "Yandex", "https://yandex.com/search/?text=%s", "https://suggest.yandex.com/suggest-ff.cgi?part=%s"), Engine("qwant", "Qwant", "https://www.qwant.com/?q=%s", null), Engine("custom", "Custom…", "", null))

class AppSettings(ctx: Context) {
    val prefs: SharedPreferences = ctx.applicationContext.getSharedPreferences("silu", Context.MODE_PRIVATE)

    private fun bool(k: String, d: Boolean): ReadWriteProperty<Any?, Boolean> = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getBoolean(k, d)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            prefs.edit().putBoolean(k, value).apply()
        }
    }
    private fun int(k: String, d: Int): ReadWriteProperty<Any?, Int> = object : ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getInt(k, d)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            prefs.edit().putInt(k, value).apply()
        }
    }
    private fun str(k: String, d: String): ReadWriteProperty<Any?, String> = object : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getString(k, d) ?: d
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            prefs.edit().putString(k, value).apply()
        }
    }

    var barBottom by bool("bar_bottom", true)
    var hideOnScroll by bool("hide_scroll", true)
    var pullRefresh by bool("pull_refresh", true)
    var theme by str("theme", "dark")
    var webDark by bool("web_dark", false)
    var desktopDefault by bool("desktop_default", false)

    var engineId by str("engine", "google")
    var customEngine by str("custom_engine", "")
    var suggestions by bool("suggest", true)

    var blockPopups by bool("block_popups", true)
    var thirdPartyCookies by bool("tp_cookies", true)
    var javascript by bool("js", true)
    var httpsOnly by bool("https_only", false)
    var safeBrowsing by bool("safe_browsing", true)

    var blockAds by bool("block_ads", true)
    var blockTrackers by bool("block_trackers", true)
    var blockedTotal by int("blocked_total", 0)

    var askDownload by bool("ask_download", true)
    var siteNotifications by bool("site_notif", true)

    var textZoom by int("text_zoom", 100)
    var forceZoom by bool("force_zoom", false)

    var wallpaper by str("wallpaper", "none")
    var wallpaperDim by int("wp_dim", 25)
    var wallpaperOpacity by int("wp_opacity", 100)
    var restoreTabs by bool("restore_tabs", true)
    var wallpaperVer by int("wallpaper_ver", 0)

    fun engine(): Engine = if (engineId == "custom") Engine("custom", "Custom", customEngine.ifBlank {
        ENGINES[0].search
    }, null) else ENGINES.firstOrNull {
        it.id == engineId
    } ?: ENGINES[0]

    fun nightMode() = if (theme == "teal") AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
    fun siteZoom(host: String): Int? = if (prefs.contains("zoom_$host")) prefs.getInt("zoom_$host", 100) else null
    fun setSiteZoom(host: String, v: Int) {
        prefs.edit().putInt("zoom_$host", v).apply()
    }
    fun notifPerm(origin: String): String = prefs.getString("notif_$origin", "default") ?: "default"
    fun setNotifPerm(origin: String, v: String) {
        prefs.edit().putString("notif_$origin", v).apply()
    }
    fun isAllowed(host: String) = prefs.getBoolean("allow_$host", false)
    fun setAllowed(host: String, v: Boolean) {
        prefs.edit().apply {
            if (v) putBoolean("allow_$host", true) else remove("allow_$host")
        }.apply()
    }

    fun clearKeys(vararg  prefixes: String) {
        val e = prefs.edit()
        prefs.all.keys.filter { k ->
            prefixes.any {
                k.startsWith(it)
            }
        }.forEach {
            e.remove(it)
        }
        e.apply()
    }
}


