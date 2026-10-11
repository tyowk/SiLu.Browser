package id.silu.browser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.WindowCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.util.concurrent.Executors

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class SettingsActivity : AppCompatActivity() {

    private lateinit var s: AppSettings
    private lateinit var body: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var titleView: TextView
    private var page = "main"
    private val io = Executors.newSingleThreadExecutor()

    private var range = 4
    private var cHistory = true
    private var cCookies = true
    private var cCache = true
    private var cDownloads = false
    private var cSite = false

    private var previewLayer: ImageView? = null
    private var previewDim: View? = null
    private var wpBitmap: Bitmap? = null

    private val titles = mapOf(
        "main" to "Settings", "appearance" to "Appearance", "search" to "Search engine",
        "home" to "Home page", "privacy" to "Privacy & security", "clear" to "Clear browsing data",
        "blocker" to "Ad & tracker blocking", "downloads" to "Downloads", "notifications" to "Notifications",
        "access" to "Accessibility", "general" to "General", "about" to "About"
    )

    private val wallpapers = listOf("none" to "None", "teal" to "Teal", "graphite" to "Graphite", "dusk" to "Dusk",
        "ocean" to "Ocean", "forest" to "Forest", "image" to "Photo")

    private fun presetColors(id: String): IntArray? = when (id) {
        "teal" -> intArrayOf(0xFF06171A.toInt(), 0xFF0F3B40.toInt(), 0xFF1C6A6A.toInt())
        "graphite" -> intArrayOf(0xFF0D0E10.toInt(), 0xFF23252B.toInt(), 0xFF3A3D46.toInt())
        "dusk" -> intArrayOf(0xFF14101F.toInt(), 0xFF2C2347.toInt(), 0xFF51407A.toInt())
        "ocean" -> intArrayOf(0xFF04121F.toInt(), 0xFF0A3558.toInt(), 0xFF1A6A96.toInt())
        "forest" -> intArrayOf(0xFF07140D.toInt(), 0xFF14402A.toInt(), 0xFF2F7A52.toInt())
        else -> null
    }

    private class Pal(val chrome: Int, val bar: Int, val surface: Int, val pill: Int, val accent: Int)
    private val dark = Pal(0xFF0B0B0D.toInt(), 0xFF17181B.toInt(), 0xFF1E1F23.toInt(), 0xFF292B31.toInt(), 0xFF8AB4F8.toInt())
    private val teal = Pal(0xFF071315.toInt(), 0xFF0E1F22.toInt(), 0xFF122529.toInt(), 0xFF1B3438.toInt(), 0xFF5FCDC4.toInt())

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) saveWallpaper(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = AppSettings(this)
        Store.init(this)
        val start = savedInstanceState?.getString("page") ?: intent.getStringExtra("page")
        if (start != null && titles.containsKey(start)) page = start

        window.statusBarColor = col(R.color.chrome)
        window.navigationBarColor = col(R.color.chrome)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            background = rippleRounded(col(R.color.surface), 24)
            contentDescription = "Back"
            setOnClickListener { goBack() }
            pressable()
        }
        titleView = TextView(this).apply {
            textSize = 32f; typeface = Typeface.DEFAULT_BOLD; setTextColor(col(R.color.text))
            setPadding(dp(24), dp(14), dp(24), dp(10))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(back, LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(dp(16), dp(8), 0, 0) })
            addView(titleView)
        }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(40)) }
        scroll = ScrollView(this).apply { addView(body); isVerticalScrollBarEnabled = false }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(col(R.color.chrome))
            addView(header)
            addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })
        render(false)
    }

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.putString("page", page) }
    override fun onPause() { super.onPause(); Store.flush() }
    override fun onDestroy() { io.shutdownNow(); super.onDestroy() }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.hold, R.anim.slide_out_right)
    }

    private fun goBack() {
        when (page) {
            "main" -> finish()
            "clear" -> go("privacy", false)
            else -> go("main", false)
        }
    }

    private fun go(p: String, forward: Boolean = true) {
        val dx = dp(18).toFloat() * (if (forward) 1 else -1)
        body.animate().cancel()
        body.animate().alpha(0f).translationX(-dx).setDuration(150).setInterpolator(Motion.exit).withEndAction {
            page = p
            render(false)
            body.translationX = dx
            body.animate().alpha(1f).translationX(0f).setDuration(320).setInterpolator(Motion.enter).start()
        }.start()
    }

    private fun render(keep: Boolean) {
        val y = if (keep) scroll.scrollY else 0
        previewLayer = null; previewDim = null
        body.removeAllViews()
        titleView.text = titles[page]
        when (page) {
            "appearance" -> appearance()
            "search" -> search()
            "home" -> home()
            "privacy" -> privacy()
            "clear" -> clear()
            "blocker" -> blocker()
            "downloads" -> downloads()
            "notifications" -> notifications()
            "access" -> access()
            "general" -> general()
            "about" -> about()
            else -> main()
        }
        scroll.post { scroll.scrollTo(0, y) }
        if (!keep) for (i in 0 until body.childCount) {
            val v = body.getChildAt(i)
            v.alpha = 0f; v.translationY = dp(16).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(36L * minOf(i, 8)).setDuration(340)
                .setInterpolator(Motion.enter).start()
        }
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text.uppercase(); textSize = 12f; setTextColor(col(R.color.sub)); letterSpacing = .06f
        typeface = Typeface.DEFAULT_BOLD; setPadding(dp(14), dp(22), 0, dp(8))
    }

    private fun section(title: String?, vararg rows: View) {
        if (title != null) body.addView(label(title))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(col(R.color.surface), 24, col(R.color.divider), 1)
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = if (title == null) dp(6) else 0 }
        }
        rows.forEachIndexed { i, r ->
            if (i > 0) card.addView(View(this).apply { setBackgroundColor(col(R.color.divider)) },
                LinearLayout.LayoutParams(MATCH, 1).apply { setMargins(dp(20), 0, dp(20), 0) })
            card.addView(r)
        }
        body.addView(card)
    }

    private fun chip(icon: Int): View {
        val c = FrameLayout(this).apply { background = rounded(col(R.color.pill), 20) }
        c.addView(ImageView(this).apply {
            setImageResource(icon); imageTintList = ColorStateList.valueOf(col(R.color.accent))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        return c
    }

    private fun rowView(title: String, summary: String?, trailing: View? = null, icon: Int? = null,
                        onClick: (() -> Unit)? = null): View {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(68); setPadding(dp(18), dp(12), dp(18), dp(12))
            if (onClick != null) { background = rippleRounded(Color.TRANSPARENT, 0); setOnClickListener { onClick() } }
        }
        if (icon != null) r.addView(chip(icon), LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(16) })
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(TextView(this).apply { text = title; textSize = 16f; setTextColor(col(R.color.text)) })
        if (summary != null) c.addView(TextView(this).apply { text = summary; textSize = 13f; setTextColor(col(R.color.sub)); setPadding(0, dp(2), 0, 0) })
        r.addView(c, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (trailing != null) r.addView(trailing)
        return r
    }

    private fun cat(title: String, sub: String, icon: Int, target: String): View {
        val chev = ImageView(this).apply { setImageResource(R.drawable.ic_forward); imageTintList = ColorStateList.valueOf(col(R.color.sub)) }
        return rowView(title, sub, chev, icon) { go(target) }
    }

    private fun styledSwitch(on: Boolean) = SwitchCompat(this).apply {
        isChecked = on
        val st = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        thumbTintList = ColorStateList(st, intArrayOf(col(R.color.accent), col(R.color.sub)))
        trackTintList = ColorStateList(st, intArrayOf(withAlpha(col(R.color.accent), 100), col(R.color.pill)))
    }

    private fun switchRow(title: String, summary: String?, get: () -> Boolean, set: (Boolean) -> Unit): View {
        val sw = styledSwitch(get())
        sw.contentDescription = title
        sw.setOnCheckedChangeListener { _, v -> set(v) }
        return rowView(title, summary, sw) { sw.toggle() }
    }

    private fun choiceRow(title: String, options: List<Pair<String, String>>, get: () -> String, set: (String) -> Unit): View {
        val current = options.firstOrNull { it.first == get() }?.second ?: ""
        val value = TextView(this).apply { text = current; textSize = 14f; setTextColor(col(R.color.accent)) }
        return rowView(title, null, value) {
            val idx = options.indexOfFirst { it.first == get() }.coerceAtLeast(0)
            MaterialAlertDialogBuilder(this).setTitle(title)
                .setSingleChoiceItems(options.map { it.second }.toTypedArray(), idx) { d, which ->
                    set(options[which].first); d.dismiss(); render(true)
                }.setNegativeButton("Cancel", null).show()
        }
    }

    private fun actionRow(title: String, summary: String?, onClick: () -> Unit) = rowView(title, summary, onClick = onClick)

    private fun infoRow(text: String) = TextView(this).apply {
        this.text = text; textSize = 13f; setTextColor(col(R.color.sub)); setPadding(dp(18), dp(14), dp(18), dp(6))
    }

    private fun checkRow(title: String, summary: String?, get: () -> Boolean, set: (Boolean) -> Unit): View {
        val cb = CheckBox(this).apply {
            isChecked = get(); contentDescription = title
            buttonTintList = ColorStateList.valueOf(col(R.color.accent))
        }
        cb.setOnCheckedChangeListener { _, v -> set(v) }
        return rowView(title, summary, cb) { cb.toggle() }
    }

    private fun sliderRow(title: String, min: Int, max: Int, step: Int, get: () -> Int, set: (Int) -> Unit): View {
        val value = TextView(this).apply { textSize = 14f; setTextColor(col(R.color.accent)) }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(this@SettingsActivity).apply { text = title; textSize = 16f; setTextColor(col(R.color.text)) },
                LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(value)
        }
        val seek = SeekBar(this).apply {
            this.max = (max - min) / step
            progress = (get() - min) / step
            contentDescription = title
            progressTintList = ColorStateList.valueOf(col(R.color.accent))
            thumbTintList = ColorStateList.valueOf(col(R.color.accent))
            progressBackgroundTintList = ColorStateList.valueOf(col(R.color.pill))
        }
        value.text = "${get()}%"
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val v = min + p * step
                value.text = "$v%"
                if (fromUser) set(v)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(10))
            addView(head); addView(seek)
        }
    }

    private fun pickerCards(vararg cards: Triple<String, Boolean, () -> View>, onPick: (String) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(14), dp(12), dp(14)) }
        for ((name, selected, preview) in cards) {
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(10), dp(10), dp(10), dp(12))
                background = rippleRounded(Color.TRANSPARENT, 20).also { }
                setOnClickListener { onPick(name) }
                pressable()
            }
            val frame = FrameLayout(this).apply {
                background = rounded(col(R.color.chrome), 18, if (selected) col(R.color.accent) else col(R.color.divider))
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            frame.addView(preview(), FrameLayout.LayoutParams(MATCH, MATCH))
            c.addView(frame, LinearLayout.LayoutParams(MATCH, dp(112)))
            c.addView(TextView(this).apply {
                text = (cards.firstOrNull { it.first == name }?.let { name } ?: name)
                textSize = 14f; setTextColor(if (selected) col(R.color.accent) else col(R.color.sub))
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setPadding(0, dp(10), 0, 0)
            })
            row.addView(c, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        return row
    }

    private fun themePreview(p: Pal): View {
        val f = FrameLayout(this).apply { background = rounded(p.chrome, 14); clipToOutline = true }
        f.addView(View(this).apply { background = rounded(p.surface, 8) },
            FrameLayout.LayoutParams(MATCH, dp(46), Gravity.TOP).apply { setMargins(dp(6), dp(6), dp(6), 0) })
        f.addView(View(this).apply { background = rounded(p.bar, 10) },
            FrameLayout.LayoutParams(MATCH, dp(34), Gravity.BOTTOM).apply { setMargins(dp(4), 0, dp(4), dp(4)) })
        f.addView(View(this).apply { background = rounded(p.pill, 8) },
            FrameLayout.LayoutParams(dp(54), dp(14), Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(12), 0, 0, dp(14)) })
        f.addView(View(this).apply { background = rounded(p.accent, 6) },
            FrameLayout.LayoutParams(dp(12), dp(12), Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dp(14), dp(15)) })
        return f
    }

    private fun barPreview(top: Boolean): View {
        val f = FrameLayout(this).apply { background = rounded(col(R.color.surface), 14); clipToOutline = true }
        f.addView(View(this).apply { background = rounded(col(R.color.accent), 8) },
            FrameLayout.LayoutParams(MATCH, dp(24), if (top) Gravity.TOP else Gravity.BOTTOM).apply {
                setMargins(dp(8), if (top) dp(8) else 0, dp(8), if (top) 0 else dp(8))
            })
        f.addView(View(this).apply { background = rounded(col(R.color.pill), 6) },
            FrameLayout.LayoutParams(MATCH, dp(10), Gravity.CENTER).apply { setMargins(dp(14), 0, dp(30), 0) })
        return f
    }

    private fun main() {
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "" }
        section(null,
            cat("Appearance", if (s.theme == "teal") "Deep Teal theme" else "Dark theme", R.drawable.ic_palette, "appearance"),
            cat("Home page", "Wallpaper and shortcuts", R.drawable.ic_home, "home"),
            cat("Search engine", s.engine().name, R.drawable.ic_search, "search")
        )
        section(null,
            cat("Privacy & security", "Cookies, HTTPS, clear data", R.drawable.ic_shield, "privacy"),
            cat("Ad & tracker blocking", if (s.blockAds || s.blockTrackers) "On" else "Off", R.drawable.ic_block, "blocker"),
            cat("Downloads", if (s.askDownload) "Ask before downloading" else "Download automatically", R.drawable.ic_download, "downloads"),
            cat("Notifications", if (s.siteNotifications) "Websites can ask" else "Blocked", R.drawable.ic_bell, "notifications")
        )
        section(null,
            cat("Accessibility", "Text size and zoom", R.drawable.ic_eye, "access"),
            cat("General", "Startup and default browser", R.drawable.ic_sliders, "general"),
            cat("About", "SiLu Browser $ver", R.drawable.ic_info, "about")
        )
    }

    private fun appearance() {
        section("Theme", pickerCards(
            Triple("Dark", s.theme != "teal") { themePreview(dark) },
            Triple("Deep Teal", s.theme == "teal") { themePreview(teal) }
        ) {
            s.theme = if (it == "Deep Teal") "teal" else "dark"
            AppCompatDelegate.setDefaultNightMode(s.nightMode())
        })
        section("Address bar", pickerCards(
            Triple("Bottom", s.barBottom) { barPreview(false) },
            Triple("Top", !s.barBottom) { barPreview(true) }
        ) { s.barBottom = it == "Bottom"; render(true) },
            switchRow("Hide bars when scrolling", "Bars return when you scroll up", { s.hideOnScroll }, { s.hideOnScroll = it }),
            switchRow("Pull to refresh", null, { s.pullRefresh }, { s.pullRefresh = it })
        )
        section("Web pages",
            switchRow("Dark mode for web pages", "Darkens pages that don't have their own dark theme", { s.webDark }, { s.webDark = it }),
            switchRow("Desktop site by default", "New tabs request the desktop version", { s.desktopDefault }, { s.desktopDefault = it })
        )
    }

    private fun search() {
        val rows = ArrayList<View>()
        rows.add(choiceRow("Search engine", ENGINES.map { it.id to it.name }, { s.engineId }, { s.engineId = it }))
        if (s.engineId == "custom") rows.add(actionRow("Custom search URL",
            s.customEngine.ifBlank { "Use %s where the search text goes" }) {
            val et = EditText(this).apply {
                hint = "https://example.com/search?q=%s"; setText(s.customEngine); setSingleLine()
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }
            MaterialAlertDialogBuilder(this).setTitle("Custom search URL")
                .setView(FrameLayout(this).apply { setPadding(dp(24), dp(8), dp(24), 0); addView(et) })
                .setPositiveButton("Save") { _, _ ->
                    val v = et.text.toString().trim()
                    if (v.contains("%s")) { s.customEngine = v; render(true) } else Toast.makeText(this, "The URL must contain %s", Toast.LENGTH_SHORT).show()
                }.setNegativeButton("Cancel", null).show()
        })
        rows.add(switchRow("Search suggestions",
            if (s.engine().suggest == null) "Not available for this engine (history and bookmarks still appear)" else "Sends what you type to the search engine",
            { s.suggestions }, { s.suggestions = it }))
        section(null, *rows.toTypedArray())
        body.addView(infoRow("Microsoft Edge uses Bing, so choose Bing for the same results."))
    }

    private fun home() {
        if (s.wallpaper == "image" && wpBitmap == null) loadWpBitmap()
        body.addView(label("Wallpaper"))
        body.addView(buildPreview())

        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(4), dp(14), dp(4), dp(4)) }
        for ((id, name) in wallpapers) {
            val sel = s.wallpaper == id
            val ring = FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(2), if (sel) col(R.color.accent) else Color.TRANSPARENT)
                }
                setPadding(dp(4), dp(4), dp(4), dp(4))
                contentDescription = name
                setOnClickListener {
                    if (id == "image") picker.launch("image/*")
                    else { s.wallpaper = id; s.wallpaperVer = s.wallpaperVer + 1; render(true) }
                }
                pressable()
            }
            val colors = presetColors(id)
            val dot = FrameLayout(this).apply {
                background = if (colors != null) GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply { shape = GradientDrawable.OVAL }
                else GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(col(R.color.pill)) }
            }
            if (colors == null) dot.addView(ImageView(this).apply {
                setImageResource(if (id == "image") R.drawable.ic_image else R.drawable.ic_block)
                imageTintList = ColorStateList.valueOf(col(R.color.sub))
            }, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
            ring.addView(dot, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                addView(ring, LinearLayout.LayoutParams(dp(52), dp(52)))
                addView(TextView(this@SettingsActivity).apply { text = name; textSize = 11f; setTextColor(col(R.color.sub)); setPadding(0, dp(4), 0, 0) })
            }
            chips.addView(col, LinearLayout.LayoutParams(dp(64), WRAP))
        }
        body.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) })

        section(null,
            sliderRow("Dim", 0, 80, 5, { s.wallpaperDim }, { s.wallpaperDim = it; s.wallpaperVer = s.wallpaperVer + 1; updatePreview() }),
            sliderRow("Opacity", 20, 100, 5, { s.wallpaperOpacity }, { s.wallpaperOpacity = it; s.wallpaperVer = s.wallpaperVer + 1; updatePreview() })
        )

        val rows = ArrayList<View>()
        Store.shortcuts.toList().forEach { e ->
            val del = ImageButton(this).apply {
                setImageResource(R.drawable.ic_trash); background = rippleRounded(Color.TRANSPARENT, 22)
                contentDescription = "Delete ${e.title}"
                setOnClickListener { Store.removeShortcut(e); render(true) }
            }
            rows.add(rowView(e.title, hostOf(e.url), del) {
                shortcutDialog(e) { n, u -> Store.replaceShortcut(e, n, u); render(true) }
            })
        }
        rows.add(actionRow("Add shortcut", null) { shortcutDialog(null) { n, u -> Store.addShortcut(n, u); render(true) } })
        rows.add(actionRow("Reset shortcuts", "Restore the default shortcuts") { Store.resetShortcuts(); render(true) })
        section("Shortcuts", *rows.toTypedArray())
    }

    private fun buildPreview(): View {
        val box = FrameLayout(this).apply {
            background = rounded(col(R.color.chrome), 26)
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(190)).apply { topMargin = dp(2) }
        }
        val layer = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        val dim = View(this).apply { setBackgroundColor(Color.BLACK) }
        val hero = TextView(this).apply {
            text = "SiLuyla"; textSize = 40f; setTextColor(col(R.color.text)); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); letterSpacing = -.03f
        }
        box.addView(layer, FrameLayout.LayoutParams(MATCH, MATCH))
        box.addView(dim, FrameLayout.LayoutParams(MATCH, MATCH))
        box.addView(hero, FrameLayout.LayoutParams(MATCH, MATCH))
        previewLayer = layer; previewDim = dim
        updatePreview()
        return box
    }

    private fun updatePreview() {
        val layer = previewLayer ?: return
        val id = s.wallpaper
        val colors = presetColors(id)
        layer.setImageDrawable(when {
            colors != null -> GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors)
            id == "image" -> wpBitmap?.let { BitmapDrawable(resources, it) }
            else -> null
        })
        layer.alpha = s.wallpaperOpacity / 100f
        previewDim?.alpha = if (id == "none") 0f else s.wallpaperDim / 100f
    }

    private fun loadWpBitmap() {
        io.execute {
            try {
                val f = File(File(filesDir, "wallpaper"), "wp.jpg")
                if (!f.exists()) return@execute
                val b = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 4 })
                runOnUiThread { wpBitmap = b; updatePreview() }
            } catch (e: Exception) { }
        }
    }

    private fun privacy() {
        section("Security",
            switchRow("Always use secure connections", "Upgrade http:// pages to https:// when possible", { s.httpsOnly }, { s.httpsOnly = it }),
            switchRow("Safe Browsing", "Warn about dangerous sites", { s.safeBrowsing }, { s.safeBrowsing = it })
        )
        section("Site settings",
            switchRow("Block pop-ups", "Stops sites from opening windows on their own", { s.blockPopups }, { s.blockPopups = it }),
            switchRow("Allow third-party cookies", "Turning this off can break some sign-ins (always off in secret tabs)", { s.thirdPartyCookies }, { s.thirdPartyCookies = it }),
            switchRow("JavaScript", "Most sites need this to work", { s.javascript }, { s.javascript = it })
        )
        section("Data",
            actionRow("Clear browsing data", "History, cookies, cache and more") { go("clear") }
        )
    }

    private fun clear() {
        val ranges = listOf("0" to "Last hour", "1" to "Last 24 hours", "2" to "Last 7 days", "3" to "Last 4 weeks", "4" to "All time")
        section(null, choiceRow("Time range", ranges, { range.toString() }, { range = it.toInt() }))
        section("Select what to clear",
            checkRow("Browsing history", "Pages you've visited", { cHistory }, { cHistory = it }),
            checkRow("Cookies and site data", "Signs you out of most sites. Always applies to all time", { cCookies }, { cCookies = it }),
            checkRow("Cached images and files", "Frees up storage. Always applies to all time", { cCache }, { cCache = it }),
            checkRow("Download history", "Files stay on your device", { cDownloads }, { cDownloads = it }),
            checkRow("Site settings", "Text sizes, notification choices, blocker exceptions", { cSite }, { cSite = it })
        )
        val btn = Button(this).apply {
            text = "Clear data"; isAllCaps = false; setTextColor(col(R.color.chrome)); textSize = 16f
            background = rippleRounded(col(R.color.accent), 28)
            stateListAnimator = null
            setOnClickListener { confirmClear() }
            pressable()
        }
        body.addView(btn, LinearLayout.LayoutParams(MATCH, dp(56)).apply { topMargin = dp(22) })
        body.addView(infoRow("Secret tabs never save history or cookies, so there is nothing to clear for them."))
    }

    private fun confirmClear() {
        val parts = ArrayList<String>()
        if (cHistory) parts.add("browsing history")
        if (cCookies) parts.add("cookies and site data")
        if (cCache) parts.add("cached files")
        if (cDownloads) parts.add("download history")
        if (cSite) parts.add("site settings")
        if (parts.isEmpty()) { Toast.makeText(this, "Select something to clear", Toast.LENGTH_SHORT).show(); return }
        MaterialAlertDialogBuilder(this).setTitle("Clear selected data?")
            .setMessage("This will clear your " + parts.joinToString(", ") + ".")
            .setPositiveButton("Clear") { _, _ -> doClear() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun doClear() {
        val now = System.currentTimeMillis()
        val since = when (range) { 0 -> now - 3_600_000L; 1 -> now - 86_400_000L; 2 -> now - 7 * 86_400_000L; 3 -> now - 28 * 86_400_000L; else -> 0L }
        if (cHistory) Store.clearHistory(since)
        if (cDownloads) Store.clearDownloads(since)
        if (cCookies) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }
        if (cCache) WebView(this).apply { clearCache(true); destroy() }
        if (cSite) s.clearKeys("zoom_", "notif_", "allow_")
        Toast.makeText(this, "Data cleared", Toast.LENGTH_SHORT).show()
    }

    private fun blocker() {
        section("Blocking",
            switchRow("Block ads", "Ad networks and common ad containers", { s.blockAds }, { s.blockAds = it }),
            switchRow("Block trackers", "Analytics and tracking scripts", { s.blockTrackers }, { s.blockTrackers = it })
        )
        section("Statistics and exceptions",
            rowView("Blocked so far", "${s.blockedTotal} requests"),
            actionRow("Reset exceptions", "Turn blocking back on for every site") {
                s.clearKeys("allow_"); Toast.makeText(this, "Exceptions reset", Toast.LENGTH_SHORT).show()
            }
        )
        body.addView(infoRow("To turn blocking off for one site, open the menu on that page and tap \"Ad & tracker blocking\". Blocking uses a built-in domain list and can't catch ads served from the site's own domain."))
    }

    private fun downloads() {
        section(null,
            switchRow("Always ask before downloading", "Confirm and rename each file before it's saved", { s.askDownload }, { s.askDownload = it }),
            actionRow("Open Downloads folder", "Files are saved to your device's Downloads folder") {
                try { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) } catch (e: Exception) { }
            }
        )
        body.addView(infoRow("Download progress and completion appear in your notifications. You can also review them in the app menu under Downloads."))
    }

    private fun notifications() {
        section(null,
            switchRow("Website notifications", "Let sites ask to show notifications", { s.siteNotifications }, { s.siteNotifications = it }),
            actionRow("Reset site notification choices", "Sites will ask again") {
                s.clearKeys("notif_"); Toast.makeText(this, "Choices reset", Toast.LENGTH_SHORT).show()
            },
            actionRow("System notification settings", "Manage downloads and website notification channels") {
                try {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                } catch (e: Exception) { }
            }
        )
        body.addView(infoRow("Websites can show notifications while SiLu Browser is running. Background push notifications aren't supported yet."))
    }

    private fun access() {
        val sample = TextView(this).apply {
            text = "The quick brown fox jumps over the lazy dog."
            setTextColor(col(R.color.text)); setPadding(dp(18), dp(4), dp(18), dp(18)); textSize = 16f * s.textZoom / 100f
        }
        val head = TextView(this).apply { text = "Text size: ${s.textZoom}%"; textSize = 16f; setTextColor(col(R.color.text)) }
        val seek = SeekBar(this).apply {
            max = 15; progress = (s.textZoom - 50) / 10
            contentDescription = "Text size"
            progressTintList = ColorStateList.valueOf(col(R.color.accent))
            thumbTintList = ColorStateList.valueOf(col(R.color.accent))
            progressBackgroundTintList = ColorStateList.valueOf(col(R.color.pill))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = 50 + p * 10
                    head.text = "Text size: $v%"
                    sample.textSize = 16f * v / 100f
                    if (fromUser) s.textZoom = v
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(8))
            addView(head); addView(seek)
        }
        section("Text size", box, sample)
        section("Zoom",
            switchRow("Force enable zoom", "Pinch-to-zoom on every site, even ones that block it", { s.forceZoom }, { s.forceZoom = it }),
            actionRow("Reset per-site text sizes", "Use the default size everywhere") {
                s.clearKeys("zoom_"); Toast.makeText(this, "Reset", Toast.LENGTH_SHORT).show()
            }
        )
        body.addView(infoRow("You can also change the text size for one site from the page menu."))
    }

    private fun general() {
        section(null,
            switchRow("Restore tabs on startup", "Reopen the tabs you had open", { s.restoreTabs }, { s.restoreTabs = it }),
            actionRow("Make SiLu Browser your default browser", null) {
                try { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) } catch (e: Exception) { }
            }
        )
    }

    private fun about() {
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "" }
        val wv = WebView.getCurrentWebViewPackage()?.versionName ?: "unknown"
        section(null,
            rowView("SiLu Browser", "Version $ver"),
            rowView("Developer", "SiLuyla Development"),
            rowView("Web engine", "Chromium (Android System WebView $wv)"),
            actionRow("Passkeys", "Not supported") {
                MaterialAlertDialogBuilder(this).setTitle("Passkeys not supported")
                    .setMessage("SiLu Browser doesn't support passkeys yet. When a site asks for one, choose \"another way\" " +
                        "(password, code or QR code), or sign in once with Chrome.")
                    .setPositiveButton("OK", null).show()
            }
        )
    }

    private fun saveWallpaper(uri: Uri) {
        io.execute {
            try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                var sample = 1
                while (opts.outWidth / sample > 1800 || opts.outHeight / sample > 1800) sample *= 2
                var bmp: Bitmap = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: throw IllegalStateException("decode failed")
                val rot = contentResolver.openInputStream(uri)?.use {
                    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
                } ?: 0f
                if (rot != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
                val dir = File(filesDir, "wallpaper").apply { mkdirs() }
                File(dir, "wp.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                val small = Bitmap.createScaledBitmap(bmp, maxOf(1, bmp.width / 4), maxOf(1, bmp.height / 4), true)
                runOnUiThread {
                    wpBitmap = small
                    s.wallpaper = "image"
                    s.wallpaperVer = s.wallpaperVer + 1
                    render(true)
                    Toast.makeText(this, "Wallpaper updated", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Couldn't use that image", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}