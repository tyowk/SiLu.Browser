package id.silu.browser
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject

class Entry(val title: String, val url: String, val ts: Long = System.currentTimeMillis())
class Dl(val id: Long, val name: String, val url: String, val ts: Long)

object Store {
    private lateinit var prefs: SharedPreferences
    private val ui = Handler(Looper.getMainLooper())
    private val dirty = HashSet<String>()
    private val flushTask = Runnable {
        flush()
    }

    val history = ArrayList<Entry>()
    val bookmarks = ArrayList<Entry>()
    val shortcuts = ArrayList<Entry>()
    val downloads = ArrayList<Dl>()

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("silu", Context.MODE_PRIVATE)
        load("hist", history)
        load("bm", bookmarks)
        if (prefs.contains("shortcuts")) load("shortcuts", shortcuts) else shortcuts.addAll(defaultShortcuts())
        try {
            val a = JSONArray(prefs.getString("dl", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                downloads.add(Dl(o.getLong("i"), o.getString("n"), o.optString("u"), o.optLong("s")))
            }
        } catch (e: Exception) {}
    }

    private fun defaultShortcuts() = listOf(Entry("Google", "https://www.google.com"), Entry("YouTube", "https://www.youtube.com"), Entry("Wikipedia", "https://en.wikipedia.org"), Entry("GitHub", "https://github.com"), Entry("Reddit", "https://www.reddit.com"), Entry("Amazon", "https://www.amazon.com"), Entry("Maps", "https://maps.google.com"), Entry("Gmail", "https://mail.google.com"))

    private fun load(k: String, into: MutableList<Entry>) {
        try {
            val a = JSONArray(prefs.getString(k, "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                into.add(Entry(o.optString("t"), o.getString("u"), o.optLong("s")))
            }
        } catch (e: Exception) {}
    }

    private fun enc(list: List<Entry>): String {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("t", it.title).put("u", it.url).put("s", it.ts))
        }
        return a.toString()
    }

    private fun mark(k: String) {
        dirty.add(k)
        ui.removeCallbacks(flushTask)
        ui.postDelayed(flushTask, 1000)
    }

    fun flush() {
        if (!::prefs.isInitialized || dirty.isEmpty()) return
        val e = prefs.edit()
        for (k in dirty) {
            when(k) {
                "hist" -> e.putString(k, enc(history))
                "bm" -> e.putString(k, enc(bookmarks))
                "shortcuts" -> e.putString(k, enc(shortcuts))
                "dl" -> {
                    val a = JSONArray()
                    downloads.forEach {
                        a.put(JSONObject().put("i", it.id).put("n", it.name).put("u", it.url).put("s", it.ts))
                    }
                    e.putString(k, a.toString())
                }
            }
        }
        e.apply()
        dirty.clear()
    }

    fun addHistory(title: String, url: String) {
        if (history.isNotEmpty() && history.last().url == url) {
            val old = history.last()
            history[history.size - 1] = Entry(title.ifBlank {
                old.title
            }, url)
        } else {
            history.removeAll {
                it.url == url
            }
            history.add(Entry(title, url))
            while (history.size > 500) history.removeAt(0)
        }
        mark("hist")
    }
    fun removeHistory(url: String) {
        history.removeAll {
            it.url == url
        }
        mark("hist")
    }
    fun clearHistory(since: Long) {
        history.removeAll {
            it.ts >= since
        }
        mark("hist")
        flush()
    }

    fun isBookmarked(url: String) = bookmarks.any {
        it.url == url
    }
    fun toggleBookmark(title: String, url: String): Boolean {
        val had = bookmarks.removeAll {
            it.url == url
        }
        if (!had) bookmarks.add(Entry(title, url))
        mark("bm")
        return !had
    }
    fun removeBookmark(url: String) {
        bookmarks.removeAll {
            it.url == url
        }
        mark("bm")
    }

    fun addShortcut(title: String, url: String) {
        shortcuts.add(Entry(title, url))
        mark("shortcuts")
    }
    fun replaceShortcut(old: Entry, title: String, url: String) {
        val i = shortcuts.indexOf(old)
        if (i >= 0) shortcuts[i] = Entry(title, url) else shortcuts.add(Entry(title, url))
        mark("shortcuts")
    }
    fun removeShortcut(e: Entry) {
        shortcuts.remove(e)
        mark("shortcuts")
    }
    fun resetShortcuts() {
        shortcuts.clear()
        shortcuts.addAll(defaultShortcuts())
        mark("shortcuts")
    }

    fun addDownload(d: Dl) {
        downloads.add(d)
        mark("dl")
    }
    fun removeDownload(id: Long) {
        downloads.removeAll {
            it.id == id
        }
        mark("dl")
    }
    fun clearDownloads(since: Long) {
        downloads.removeAll {
            it.ts >= since
        }
        mark("dl")
        flush()
    }
}

