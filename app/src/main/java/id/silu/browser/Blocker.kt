package id.silu.browser
import android.content.Context

object Blocker {
    @Volatile
    private var ads: Set<String> = emptySet()
    @Volatile
    private var trackers: Set<String> = emptySet()
    @Volatile
    private var started = false

    fun load(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        Thread {
            ads = read(app, "ads.txt")
            trackers = read(app, "trackers.txt")
        }.start()
    }

    private fun read(ctx: Context, name: String): Set<String> = try {
        ctx.assets.open(name).bufferedReader().useLines { lines ->
            lines.map {
                it.trim().lowercase()
            }.filter {
                it.isNotEmpty() && !it.startsWith("#")
            }.toHashSet()
        }
    } catch (e: Exception) {
        emptySet()
    }

    fun check(host: String, blockAds: Boolean, blockTrackers: Boolean): Boolean {
        var h = host.lowercase()
        val a = ads
        val t = trackers
        while (true) {
            if (blockAds && a.contains(h)) return true
            if (blockTrackers && t.contains(h)) return true
            val i = h.indexOf('.')
            if (i < 0) return false
            val rest = h.substring(i + 1)
            if (!rest.contains('.')) return false
            h = rest
        }
    }
}

