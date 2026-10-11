package id.silu.browser

import android.Manifest
import android.app.DownloadManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import android.print.PrintAttributes
import android.print.PrintManager
import android.speech.RecognizerIntent
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.util.Locale
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.ProfileStore
import androidx.webkit.ScriptHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray
import org.json.JSONObject

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val SECRET_PROFILE = "silu_secret"
private const val ERR_TITLE = "Can't open page"
private val SCHEME_RE = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")
private val LOCAL_RE = Regex("^(localhost|\\d{1,3}(\\.\\d{1,3}){3})(:\\d+)?(/.*)?$")
private val URL_RE = Regex("^[^\\s]+\\.[^\\s]{2,}$")
private val BAD_NAME = Regex("[\\\\/:*?\"<>|]")
private val INTERNAL_SCHEMES = setOf("http", "https", "about", "data", "blob", "javascript")

private fun EditText.onChange(f: (String) -> Unit) = addTextChangedListener(object : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    override fun afterTextChanged(s: Editable?) = f(s?.toString() ?: "")
})

class MainActivity : AppCompatActivity() {

    class Tab(val secret: Boolean) {
        var web: WebView? = null
        var title = ""
        var url = ""
        var loading = false
        var desktop = false
        var thumb: Bitmap? = null
        var icon: Bitmap? = null
        var scriptSig = ""
        val scripts = ArrayList<ScriptHandler>()
        val blocked = AtomicInteger(0)
        @Volatile var allow = false
        var upgradedFrom: String? = null
        @Volatile var innerScroll = false
        var offlineUrl: String? = null
        val console = ArrayList<DevTools.Entry>()
    }

    class Sugg(val kind: Int, val text: String, val url: String? = null)

    companion object {
        const val HOME = "https://appassets.androidplatform.net/assets/home.html"
        const val ACTION_SEARCH = "id.silu.browser.SEARCH"
        const val ACTION_SEARCH_SECRET = "id.silu.browser.SEARCH_SECRET"
        const val OFFLINE_TITLE = "Offline"

        const val SHIM = "var unsafeWindow=window;var GM_addStyle=function(c){var s=document.createElement('style');s.textContent=c;(document.head||document.documentElement).appendChild(s);return s};var GM_log=function(){console.log.apply(console,arguments)};"

        const val TEMPLATE = """// ==UserScript==
// @name        Example: tint headings
// @match       https://example.com/*
// ==/UserScript==

GM_addStyle("h1 { color: #3E91FF !important; }");
"""

        const val DESKTOP_JS = """(function(){
if(window.__siluDeskOn)return;window.__siluDeskOn=1;
try{Object.defineProperty(navigator,'userAgentData',{get:function(){return undefined}})}catch(e){}
try{Object.defineProperty(navigator,'platform',{get:function(){return 'Linux x86_64'}})}catch(e){}
var W=1280,busy=false;
function scale(){var sw=screen.width||W;return Math.max(.1,Math.min(1,sw/W))}
function content(z){return 'width='+W+', initial-scale='+z.toFixed(4)+', minimum-scale=.1, maximum-scale=10, user-scalable=yes'}
function fix(){if(busy)return;var h=document.head||document.documentElement;if(!h)return;busy=true;
var all=document.querySelectorAll('meta[name=viewport]'),m=all[0];
for(var i=1;i<all.length;i++)all[i].remove();
if(!m){m=document.createElement('meta');m.name='viewport';h.appendChild(m)}
var c=content(scale());if(m.getAttribute('content')!==c)m.setAttribute('content',c);busy=false}
function kick(){fix();var m=document.querySelector('meta[name=viewport]');if(!m)return;
busy=true;var z=scale();m.setAttribute('content',content(Math.min(1,z+.05)));m.setAttribute('content',content(z));busy=false}
window.__siluKick=kick;
fix();
document.addEventListener('DOMContentLoaded',fix);window.addEventListener('load',fix);
try{new MutationObserver(fix).observe(document,{childList:true,subtree:true,attributes:true,attributeFilter:['content','name']})}catch(e){}
})();"""

        const val DESKTOP_KICK_JS = "window.__siluKick&&window.__siluKick();"

        const val SCROLL_JS = """(function(){
if(window.__siluSc)return;window.__siluSc=1;
function post(v){try{siluScroll.postMessage(v?'1':'0')}catch(e){}}
function chk(ev){var p=ev.composedPath?ev.composedPath():[ev.target],v=false;
for(var i=0;i<p.length;i++){var e=p[i];if(e&&e.nodeType===1&&e.scrollTop>0){v=true;break}}
post(v)}
document.addEventListener('touchstart',chk,{capture:true,passive:true});
})();"""

        const val COSMETIC_JS = """(function(){var s=document.createElement('style');
s.textContent='ins.adsbygoogle,.adsbygoogle,[id^="google_ads_"],[id^="div-gpt-ad"],[id^="ad-slot"],.ad-slot,.ad-banner,.ad-container,iframe[src*="doubleclick.net"],iframe[src*="googlesyndication.com"],iframe[src*="adservice"]{display:none!important}';
(document.head||document.documentElement).appendChild(s)})();"""

        const val FORCE_ZOOM_JS = """(function(){var m=document.querySelector('meta[name=viewport]');if(m){var c=(m.getAttribute('content')||'').replace(/(user-scalable|maximum-scale|minimum-scale)\s*=\s*[^,;]+[,;]?\s*/gi,'').replace(/[,;\s]+${'$'}/,'');m.setAttribute('content',c+(c?', ':'')+'user-scalable=yes, maximum-scale=10')}})();"""

        const val PASSKEY_JS = """(function(){
if(window.__siluPk)return;window.__siluPk=1;
var c=navigator.credentials;if(!c)return;
function tell(){try{siluPk.postMessage('pk')}catch(e){}}
function no(){return Promise.reject(new DOMException('Passkeys are not supported in SiLu Browser.','NotAllowedError'))}
['create','get'].forEach(function(k){var f=c[k];if(typeof f!=='function')return;
c[k]=function(o){if(o&&o.publicKey){if(o.mediation!=='conditional')tell();return no()}return f.apply(c,arguments)}});
try{if(window.PublicKeyCredential){PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable=function(){return Promise.resolve(false)};PublicKeyCredential.isConditionalMediationAvailable=function(){return Promise.resolve(false)}}}catch(e){}
})();"""

        const val NOTIF_JS = """(function(){
if(!window.siluBridge||window.__siluN)return;window.__siluN=1;
var perm='default',cb=null;
function post(o){try{siluBridge.postMessage(JSON.stringify(o))}catch(e){}}
siluBridge.onmessage=function(e){try{var d=JSON.parse(e.data);if(d.t==='perm'){perm=d.v;if(cb){var f=cb;cb=null;f(d.v)}}}catch(x){}};
function N(title,o){o=o||{};this.title=String(title);this.body=String(o.body||'');this.tag=String(o.tag||'');this.onclick=null;this.onshow=null;this.onclose=null;this.onerror=null;
post({t:'show',title:this.title,body:this.body,tag:this.tag});var s=this;setTimeout(function(){if(typeof s.onshow==='function')s.onshow({type:'show'})},0)}
N.prototype.close=function(){};N.prototype.addEventListener=function(){};N.prototype.removeEventListener=function(){};
Object.defineProperty(N,'permission',{get:function(){return perm}});
N.requestPermission=function(f){return new Promise(function(res){cb=function(v){if(typeof f==='function')f(v);res(v)};post({t:'perm'})})};
N.maxActions=2;window.Notification=N;
try{if(window.ServiceWorkerRegistration){ServiceWorkerRegistration.prototype.showNotification=function(t,o){post({t:'show',title:String(t),body:String((o&&o.body)||''),tag:String((o&&o.tag)||'')});return Promise.resolve()}}}catch(e){}
post({t:'state'});
})();"""
    }

    private lateinit var cfg: AppSettings
    private lateinit var mobileUA: String
    private lateinit var desktopUA: String
    private lateinit var assetLoader: WebViewAssetLoader
    private val tabs = mutableListOf<Tab>()
    private var cur: Tab? = null
    private var showSecretTabs = false
    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val suggestToken = Any()
    @Volatile private var adsOn = false
    @Volatile private var trkOn = false

    private lateinit var root: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var progress: ProgressBar
    private lateinit var topSlot: FrameLayout
    private lateinit var bottomSlot: FrameLayout
    private lateinit var addrRow: View
    private lateinit var pill: View
    private lateinit var urlBox: EditText
    private lateinit var lock: ImageView
    private lateinit var secretIcon: ImageView
    private lateinit var actionBtn: ImageButton
    private lateinit var navBar: View
    private lateinit var backBtn: ImageButton
    private lateinit var fwdBtn: ImageButton
    private lateinit var menuBtn: View
    private lateinit var tabCount: TextView
    private lateinit var suggestScroll: View
    private lateinit var suggestList: LinearLayout
    private lateinit var findBar: View
    private lateinit var findInput: EditText
    private lateinit var findCount: TextView
    private lateinit var tabsOverlay: View
    private lateinit var tabsTitle: TextView
    private lateinit var segTabs: TextView
    private lateinit var segSecret: TextView
    private lateinit var tabsGrid: LinearLayout
    private lateinit var pageOverlay: View
    private lateinit var pageTitle: TextView
    private lateinit var pageList: LinearLayout
    private lateinit var fullscreen: FrameLayout

    private var popup: PopupWindow? = null
    private var quiet = false
    private var barsShown = true
    private var scrollAcc = 0
    private var lastToggle = 0L
    private var suggestSeq = 0
    private var lastSecretUi = false
    private var pageKind = ""
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var permCb: ((Boolean) -> Unit)? = null
    private val lastNotif = HashMap<String, Long>()
    private val closed = ArrayList<String>()
    private var actionRes = 0
    private lateinit var bottomBar: View

    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val cb = fileCb
        fileCb = null
        if (cb != null) {
            val uris = WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data)
            if (uris == null || uris.isEmpty()) cb.onReceiveValue(null)
            else io.execute {
                val out = copyForUpload(uris)
                runOnUiThread { cb.onReceiveValue(out) }
            }
        }
    }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val cb = permCb
        permCb = null
        cb?.invoke(r.values.all { it })
    }

    private val voice = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == RESULT_OK && !text.isNullOrBlank()) navigate(normalize(text))
    }

    private fun <T : View> bind(id: Int): T = findViewById(id)
    private val night get() = true
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun isHome(u: String) = u.startsWith(HOME)
    private fun secretSupported() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    private fun notifSupported() = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    private fun cookiesFor(t: Tab?): CookieManager =
        if (t?.secret == true) ProfileStore.getInstance().getProfile(SECRET_PROFILE)?.cookieManager ?: CookieManager.getInstance()
        else CookieManager.getInstance()

    private fun homeUrl(secret: Boolean): String {
        val sc = JSONArray()
        Store.shortcuts.take(12).forEach { sc.put(JSONArray().put(it.title).put(it.url)) }
        return HOME + "?th=" + (if (cfg.theme == "teal") "teal" else "dark") + "&e=" + Uri.encode(cfg.engine().search) +
            "&s=" + Uri.encode(sc.toString()) + "&wp=" + Uri.encode(cfg.wallpaper) + "&v=" + cfg.wallpaperVer +
            "&wd=" + cfg.wallpaperDim + "&wo=" + cfg.wallpaperOpacity + (if (secret) "&secret=1" else "")
    }

    private fun normalize(raw: String): String {
        val v = raw.trim()
        if (v.isEmpty()) return homeUrl(cur?.secret == true)
        if (SCHEME_RE.matches(v)) return v
        if (!v.contains(' ')) {
            if (LOCAL_RE.matches(v)) return "http://$v"
            if (URL_RE.matches(v)) return "https://$v"
        }
        return cfg.engine().search.replace("%s", Uri.encode(v))
    }

    private fun showKeyboard() {
        WindowCompat.getInsetsController(window, urlBox).show(WindowInsetsCompat.Type.ime())
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(urlBox, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        WindowCompat.getInsetsController(window, urlBox).hide(WindowInsetsCompat.Type.ime())
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(urlBox.windowToken, 0)
    }

    private fun releaseFocus() {
        hideKeyboard()
        if (urlBox.hasFocus()) urlBox.clearFocus()
        if (findInput.hasFocus()) findInput.clearFocus()
        root.requestFocus()
    }

    private fun setBox(text: String) { quiet = true; urlBox.setText(text); quiet = false }

    private fun copy(text: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("SiLu", text))
        toast("Copied")
    }

    private fun share(text: String) =
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, null))

    private fun askPerms(p: List<String>, cb: (Boolean) -> Unit) {
        if (p.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) { cb(true); return }
        permCb = cb
        permLauncher.launch(p.toTypedArray())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cfg = AppSettings(this)
        Store.init(this)
        Blocker.load(this)
        cleanUploads()
        if (secretSupported()) try { ProfileStore.getInstance().deleteProfile(SECRET_PROFILE) } catch (e: Exception) { }
        setContentView(R.layout.activity_main)

        val ua = WebSettings.getDefaultUserAgent(this)
        mobileUA = ua.replace("; wv", "").replace("Version/4.0 ", "")
        val ver = Regex("Chrome/([\\d.]+)").find(ua)?.groupValues?.get(1) ?: "124.0.0.0"
        desktopUA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$ver Safari/537.36"

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/files/", WebViewAssetLoader.InternalStoragePathHandler(this, File(filesDir, "wallpaper").apply { mkdirs() }))
            .build()

        window.statusBarColor = col(R.color.bar)
        window.navigationBarColor = col(R.color.bar)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }

        root = bind(R.id.root); content = bind(R.id.content); swipe = bind(R.id.swipe)
        progress = bind(R.id.progress); topSlot = bind(R.id.topSlot); bottomSlot = bind(R.id.bottomSlot)
        bottomBar = bind(R.id.bottomBar)
        navBar = bind(R.id.navBar); backBtn = bind(R.id.btnBack); fwdBtn = bind(R.id.btnFwd)
        menuBtn = bind(R.id.btnMenu); tabCount = bind(R.id.tabCount)
        suggestScroll = bind(R.id.suggestScroll); suggestList = bind(R.id.suggestList)
        findBar = bind(R.id.findBar); findInput = bind(R.id.findInput); findCount = bind(R.id.findCount)
        tabsOverlay = bind(R.id.tabsOverlay); tabsTitle = bind(R.id.tabsTitle)
        segTabs = bind(R.id.segTabs); segSecret = bind(R.id.segSecret); tabsGrid = bind(R.id.tabsGrid)
        pageOverlay = bind(R.id.pageOverlay); pageTitle = bind(R.id.pageTitle); pageList = bind(R.id.pageList)
        fullscreen = bind(R.id.fullscreen)

        addrRow = layoutInflater.inflate(R.layout.view_addr, null)
        pill = addrRow.findViewById(R.id.pill)
        urlBox = addrRow.findViewById(R.id.url)
        lock = addrRow.findViewById(R.id.lock)
        secretIcon = addrRow.findViewById(R.id.secretIcon)
        actionBtn = addrRow.findViewById(R.id.action)
        placeAddr()

        urlBox.setOnFocusChangeListener { _, focus ->
            if (focus) {
                setBox(cur?.url?.takeIf { !isHome(it) } ?: "")
                urlBox.post { urlBox.selectAll(); showKeyboard() }
                lock.visibility = View.GONE
                onQueryChanged(urlBox.text.toString())
            } else {
                suggestScroll.visibility = View.GONE
                ui.removeCallbacksAndMessages(suggestToken)
                updateUi()
            }
            focusPill(focus)
            updateAction()
        }
        pill.setOnClickListener { urlBox.requestFocus() }
        urlBox.onChange { q ->
            if (!quiet && urlBox.hasFocus()) onQueryChanged(q)
            updateAction()
        }
        urlBox.setOnEditorActionListener { _, id, ev ->
            if (id == EditorInfo.IME_ACTION_GO || (ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN)) {
                navigate(normalize(urlBox.text.toString())); true
            } else false
        }
        actionBtn.setOnClickListener {
            if (urlBox.hasFocus()) { if (urlBox.text.isNotEmpty()) urlBox.setText("") else startVoice() }
            else cur?.let { t -> t.web?.let { w -> if (t.loading) w.stopLoading() else w.reload() } }
        }
        backBtn.setOnClickListener { cur?.web?.let { if (it.canGoBack()) it.goBack() } }
        fwdBtn.setOnClickListener { cur?.web?.let { if (it.canGoForward()) it.goForward() } }
        findViewById<View>(R.id.btnHome).setOnClickListener { cur?.let { navigate(homeUrl(it.secret)) } }
        findViewById<View>(R.id.btnTabs).setOnClickListener { showTabs() }
        menuBtn.setOnClickListener { showMenu() }
        listOf(backBtn, fwdBtn, findViewById<View>(R.id.btnHome), findViewById<View>(R.id.btnTabs), menuBtn, actionBtn).forEach { it.pressable() }

        swipe.setColorSchemeColors(col(R.color.accent))
        swipe.setProgressBackgroundColorSchemeColor(col(R.color.surface))
        swipe.setOnRefreshListener {
            cur?.web?.reload()
            ui.postDelayed({ swipe.isRefreshing = false }, 1500)
        }
        swipe.setOnChildScrollUpCallback { _, _ -> cur?.let { isHome(it.url) || (it.web?.scrollY ?: 0) > 0 || it.innerScroll } ?: false }

        findInput.onChange { q ->
            val w = cur?.web ?: return@onChange
            if (q.isEmpty()) { w.clearMatches(); findCount.text = "" } else w.findAllAsync(q)
        }
        findInput.setOnEditorActionListener { _, _, _ -> cur?.web?.findNext(true); true }
        findViewById<View>(R.id.findUp).setOnClickListener { cur?.web?.findNext(false) }
        findViewById<View>(R.id.findDown).setOnClickListener { cur?.web?.findNext(true) }
        findViewById<View>(R.id.findClose).setOnClickListener { closeFind() }

        segTabs.setOnClickListener { switchTabsSegment(false) }
        segSecret.setOnClickListener { switchTabsSegment(true) }
        findViewById<View>(R.id.btnTabsDone).setOnClickListener { closeTabs() }
        findViewById<View>(R.id.btnNewTab).setOnClickListener {
            val secret = showSecretTabs
            closeTabs()
            addTab(null, secret)
        }
        findViewById<View>(R.id.btnCloseAll).setOnClickListener { confirmCloseAll(showSecretTabs) }
        findViewById<View>(R.id.pageBack).setOnClickListener { closePage() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> hideCustom()
                    pageOverlay.visibility == View.VISIBLE -> closePage()
                    tabsOverlay.visibility == View.VISIBLE -> closeTabs()
                    urlBox.hasFocus() -> releaseFocus()
                    findBar.visibility == View.VISIBLE -> closeFind()
                    cur?.web?.canGoBack() == true -> cur?.web?.goBack()
                    tabs.count { !it.secret } > 1 || cur?.secret == true -> cur?.let { closeTab(it) }
                    else -> finish()
                }
            }
        })

        val saved = if (!cfg.restoreTabs) JSONArray() else try { JSONArray(cfg.prefs.getString("session", "[]")) } catch (e: Exception) { JSONArray() }
        for (i in 0 until saved.length()) {
            val t = Tab(false)
            val u = saved.getString(i)
            t.url = if (isHome(u)) homeUrl(false) else u
            t.desktop = cfg.desktopDefault
            tabs.add(t)
        }
        val link = intent?.dataString
        when {
            link != null && link.startsWith("http") -> addTab(link)
            tabs.isEmpty() -> addTab(null)
            else -> select(tabs[cfg.prefs.getInt("active", 0).coerceIn(0, tabs.lastIndex)])
        }
        if (savedInstanceState == null) handleWidget(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val link = intent.dataString
        if (link != null && link.startsWith("http")) addTab(link)
        handleWidget(intent)
    }

    private fun handleWidget(i: Intent?) {
        val secret = when (i?.action) {
            ACTION_SEARCH -> false
            ACTION_SEARCH_SECRET -> true
            else -> return
        }
        i?.action = null
        if (pageOverlay.visibility == View.VISIBLE) closePage()
        if (tabsOverlay.visibility == View.VISIBLE) closeTabs()
        val c = cur
        if (secret) { if (c == null || !c.secret || !isHome(c.url)) addTab(null, true) }
        else if (c == null || c.secret || !isHome(c.url)) addTab(null)
        urlBox.postDelayed({
            urlBox.requestFocus()
            urlBox.selectAll()
            WindowCompat.getInsetsController(window, urlBox).show(WindowInsetsCompat.Type.ime())
        }, 250)
    }

    internal fun prefs() = cfg.prefs

    override fun onPause() {
        super.onPause()
        watchNetwork(false)
        val normal = tabs.filter { !it.secret }
        val arr = JSONArray()
        normal.forEach { arr.put(it.url) }
        cfg.prefs.edit().putString("session", arr.toString()).putInt("active", normal.indexOf(cur).coerceAtLeast(0)).apply()
        Store.flush()
        CookieManager.getInstance().flush()
        cur?.web?.onPause()
        cur?.web?.pauseTimers()
    }

    override fun onResume() {
        super.onResume()
        watchNetwork(true)
        cur?.web?.onResume()
        cur?.web?.resumeTimers()
        adsOn = cfg.blockAds
        trkOn = cfg.blockTrackers
        swipe.isEnabled = cfg.pullRefresh
        tabs.forEach { applyTabSettings(it) }
        placeAddr()
        updateUi()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) tabs.forEach { if (it !== cur) it.thumb = null }
    }

    override fun onDestroy() {
        tabs.forEach { it.web?.destroy() }
        cleanUploads()
        io.shutdownNow()
        super.onDestroy()
    }

    private fun addTab(url: String?, secret: Boolean = false, select: Boolean = true): Tab? {
        if (secret && !secretSupported()) {
            toast("Secret tabs need a newer Android System WebView. Please update it from Google Play.")
            return null
        }
        val t = Tab(secret)
        t.url = if (url == null || isHome(url)) homeUrl(secret) else url
        t.desktop = cfg.desktopDefault
        tabs.add(t)
        if (select) select(t) else updateUi()
        return t
    }

    private fun ensureWeb(t: Tab): WebView {
        t.web?.let { return it }
        if (isHome(t.url)) t.url = homeUrl(t.secret)
        val w = createWeb(t)
        w.loadUrl(t.url)
        return w
    }

    private fun select(t: Tab) {
        cur?.let { prev -> if (prev !== t) { capture(prev); prev.web?.onPause() } }
        val web = ensureWeb(t)
        if (isHome(t.url) && t.url != homeUrl(t.secret) && !t.loading) web.loadUrl(homeUrl(t.secret))
        content.removeAllViews()
        (web.parent as? ViewGroup)?.removeView(web)
        content.addView(web)
        content.animate().cancel()
        content.alpha = 0f; content.scaleX = .985f; content.scaleY = .985f
        content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(Motion.enter).start()
        web.onResume()
        cur = t
        progress.visibility = View.GONE
        releaseFocus()
        setBars(true, force = true)
        updateSecure()
        updateUi()
    }

    private fun closeTab(t: Tab) {
        val i = tabs.indexOf(t)
        if (i < 0) return
        tabs.removeAt(i)
        val wasCur = cur === t
        if (wasCur) { cur = null; content.removeAllViews() }
        t.scripts.forEach { try { it.remove() } catch (e: Exception) { } }
        t.web?.destroy()
        t.web = null
        if (t.secret && tabs.none { it.secret }) wipeSecret()
        if (!t.secret && t.url.startsWith("http")) {
            closed.add(t.url)
            while (closed.size > 10) closed.removeAt(0)
            snack("Tab closed", "Undo") { reopenClosed() }
        }
        when {
            tabs.isEmpty() -> addTab(null)
            wasCur -> select(tabs.getOrNull(minOf(i, tabs.lastIndex))!!)
            else -> updateUi()
        }
    }

    private fun confirmCloseAll(secret: Boolean) {
        val n = tabs.count { it.secret == secret }
        MaterialAlertDialogBuilder(this)
            .setTitle("Close all tabs?")
            .setMessage(if (n == 1) "1 tab will be closed." else "$n tabs will be closed.")
            .setPositiveButton("Close all") { _, _ -> closeAll(secret) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun closeAll(secret: Boolean) {
        val gone = tabs.filter { it.secret == secret }
        gone.forEach { t ->
            tabs.remove(t)
            if (cur === t) { cur = null; content.removeAllViews() }
            t.scripts.forEach { try { it.remove() } catch (e: Exception) { } }
            t.web?.destroy(); t.web = null
        }
        if (secret) wipeSecret()
        if (cur == null) {
            if (tabs.isEmpty()) addTab(null) else select(tabs.last())
        }
        renderTabs()
    }

    private fun wipeSecret() {
        if (!secretSupported()) return
        ui.postDelayed({
            if (tabs.none { it.secret }) try { ProfileStore.getInstance().deleteProfile(SECRET_PROFILE) } catch (e: Exception) { }
        }, 600)
    }

    private fun capture(t: Tab) {
        val w = t.web ?: return
        if (w.parent == null || w.width == 0 || w.height == 0) return
        try {
            val sc = dp(160).toFloat() / w.width
            val bmp = Bitmap.createBitmap(maxOf(1, (w.width * sc).toInt()), maxOf(1, (w.height * sc).toInt()), Bitmap.Config.RGB_565)
            val c = Canvas(bmp)
            c.scale(sc, sc)
            w.draw(c)
            t.thumb = bmp
        } catch (e: Throwable) { }
    }

    private fun updateSecure() {
        val secure = cur?.secret == true || (tabsOverlay.visibility == View.VISIBLE && showSecretTabs)
        if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun navigate(target: String) {
        val t = cur ?: return
        t.upgradedFrom = null
        ensureWeb(t).loadUrl(target)
        releaseFocus()
    }

    private fun createWeb(t: Tab): WebView {
        WebView.setWebContentsDebuggingEnabled(cfg.prefs.getBoolean("usbdebug", false))
        val web = WebView(this)
        t.web = web
        if (t.secret) {
            ProfileStore.getInstance().getOrCreateProfile(SECRET_PROFILE)
            WebViewCompat.setProfile(web, SECRET_PROFILE)
        }
        web.layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        web.setBackgroundColor(col(R.color.surface))
        web.settings.apply {
            domStorageEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(true)
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setGeolocationEnabled(true)
            setNeedInitialFocus(false)
        }
        cookiesFor(t).setAcceptCookie(true)
        if (!t.secret && notifSupported()) {
            WebViewCompat.addWebMessageListener(web, "siluBridge", setOf("*"),
                WebViewCompat.WebMessageListener { _, message, origin, isMain, reply ->
                    if (isMain) onBridge(message.data, origin.toString(), reply)
                })
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "siluPk", setOf("*"),
                WebViewCompat.WebMessageListener { _, _, _, _, _ -> onPasskeyAttempt() })
            WebViewCompat.addWebMessageListener(web, "siluScroll", setOf("*"),
                WebViewCompat.WebMessageListener { _, message, _, _, _ -> t.innerScroll = message.data == "1" })
            WebViewCompat.addWebMessageListener(web, "siluDev", setOf("*"),
                WebViewCompat.WebMessageListener { _, msg, _, isMain, _ ->
                    if (isMain && msg.data == "picked") runOnUiThread { if (!isFinishing) DevTools(this, t, 1).show() }
                })
        }
        web.webViewClient = client(t)
        web.webChromeClient = chrome(t)
        web.setDownloadListener { u, ua, cd, mime, len -> onDownload(t, u, ua, cd, mime, len) }
        web.setOnScrollChangeListener { _, _, sy, _, oy -> if (t === cur) onWebScroll(sy - oy, sy) }
        web.setFindListener { active, total, _ ->
            if (t === cur) findCount.text = if (total == 0) "0" else "${active + 1}/$total"
        }
        web.setOnLongClickListener { onLongPress(t, web) }
        applyTabSettings(t)
        return web
    }

    private fun applyTabSettings(t: Tab) {
        val web = t.web ?: return
        val s = web.settings
        s.javaScriptEnabled = cfg.javascript || isHome(t.url)
        s.javaScriptCanOpenWindowsAutomatically = !cfg.blockPopups
        s.textZoom = cfg.siteZoom(hostOf(t.url)) ?: cfg.textZoom
        s.userAgentString = if (t.desktop) desktopUA else mobileUA
        s.safeBrowsingEnabled = cfg.safeBrowsing
        cookiesFor(t).setAcceptThirdPartyCookies(web, if (t.secret) false else cfg.thirdPartyCookies)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, cfg.webDark)
        refreshScripts(t)
    }

    private fun refreshScripts(t: Tab) {
        val web = t.web ?: return
        val sig = "${t.desktop}|${cfg.blockAds}"
        if (sig == t.scriptSig) return
        t.scriptSig = sig
        t.scripts.forEach { try { it.remove() } catch (e: Exception) { } }
        t.scripts.clear()
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        fun add(js: String) {
            try { t.scripts.add(WebViewCompat.addDocumentStartJavaScript(web, js, setOf("*"))) } catch (e: Exception) { }
        }
        add(PASSKEY_JS)
        add(SCROLL_JS)
        if (t.desktop) add(DESKTOP_JS)
        if (cfg.blockAds) add(COSMETIC_JS)
        if (!t.secret && notifSupported()) add(NOTIF_JS)
    }

    private fun client(t: Tab) = object : WebViewClient() {
        override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
            assetLoader.shouldInterceptRequest(r.url)?.let { return it }
            if (!r.isForMainFrame && (adsOn || trkOn) && !t.allow) {
                val h = r.url.host
                if (h != null && Blocker.check(h, adsOn, trkOn)) {
                    t.blocked.incrementAndGet()
                    return WebResourceResponse("text/plain", "utf-8", 200, "OK", emptyMap<String, String>(), ByteArrayInputStream(ByteArray(0)))
                }
            }
            return null
        }

        override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
            val u = r.url
            val scheme = u.scheme ?: return false
            if (scheme == "silu") return true
            if (scheme == "file") return true
            if (r.isForMainFrame && (scheme == "http" || scheme == "https") && u.path?.endsWith(".user.js") == true) {
                installUserscript(u.toString()); return true
            }
            if (scheme == "http" && r.isForMainFrame && cfg.httpsOnly && !LOCAL_RE.matches(u.host ?: "")) {
                t.upgradedFrom = u.toString()
                v.loadUrl(u.buildUpon().scheme("https").build().toString())
                return true
            }
            if (scheme in INTERNAL_SCHEMES) return false
            if (r.hasGesture() || r.isRedirect) {
                try {
                    val i = if (scheme == "intent")
                        Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME).apply {
                            component = null; selector = null; addCategory(Intent.CATEGORY_BROWSABLE)
                        }
                    else Intent(Intent.ACTION_VIEW, u)
                    startActivity(i)
                } catch (e: Exception) {
                    toast("No app found to open this link")
                }
            }
            return true
        }

        override fun onPageStarted(v: WebView, u: String?, f: Bitmap?) {
            if (u == null) return
            t.url = u
            t.loading = true
            t.title = ""
            t.innerScroll = false
            if (!u.startsWith("data:")) t.console.clear()
            val n = t.blocked.getAndSet(0)
            if (n > 0) cfg.blockedTotal += n
            val h = hostOf(u)
            t.allow = cfg.isAllowed(h)
            v.settings.javaScriptEnabled = cfg.javascript || isHome(u)
            v.settings.textZoom = cfg.siteZoom(h) ?: cfg.textZoom
            if (t === cur) { setBars(true); updateUi() }
        }

        override fun onPageFinished(v: WebView, u: String?) {
            if (u == null) return
            t.url = u
            t.loading = false
            if (t === cur) { swipe.isRefreshing = false; updateUi() }
            if (isHome(u) || !u.startsWith("http") || t.title == ERR_TITLE) return
            if (!t.secret) Store.addHistory(t.title.ifBlank { hostOf(u) }, u)
            if (cfg.forceZoom) v.evaluateJavascript(FORCE_ZOOM_JS, null)
            if (t.scripts.isEmpty()) {
                v.evaluateJavascript(PASSKEY_JS, null)
                v.evaluateJavascript(SCROLL_JS, null)
                if (t.desktop) v.evaluateJavascript(DESKTOP_JS, null)
                if (cfg.blockAds) v.evaluateJavascript(COSMETIC_JS, null)
            }
            if (t.desktop) {
                v.evaluateJavascript(DESKTOP_KICK_JS, null)
                v.postDelayed({
                    if (t.desktop && t.web === v) v.evaluateJavascript(DESKTOP_KICK_JS, null)
                }, 400)
            }
            injectUserscripts(v, u)
        }

        override fun doUpdateVisitedHistory(v: WebView, u: String?, isReload: Boolean) {
            if (u == null) return
            t.url = u
            if (t === cur) updateUi()
        }

        override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
            if (!r.isForMainFrame) return
            if (!isOnline()) showOffline(v, t, r.url.toString())
            else showError(v, t, r.url.toString(), e.description.toString(), e.errorCode)
        }

        override fun onReceivedSslError(v: WebView, h: SslErrorHandler, e: SslError) {
            h.cancel()
            showError(v, t, e.url ?: v.url ?: "", "net::ERR_CERT_AUTHORITY_INVALID", WebViewClient.ERROR_FAILED_SSL_HANDSHAKE)
        }

        override fun onReceivedHttpAuthRequest(v: WebView, h: HttpAuthHandler, hostName: String, realm: String) {
            val u = EditText(this@MainActivity).apply { hint = "Username"; setSingleLine() }
            val p = EditText(this@MainActivity).apply {
                hint = "Password"; setSingleLine()
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            val box = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(8), dp(24), 0)
                addView(u); addView(p)
            }
            MaterialAlertDialogBuilder(this@MainActivity).setTitle("Sign in to $hostName").setMessage(realm).setView(box)
                .setPositiveButton("Sign in") { _, _ -> h.proceed(u.text.toString(), p.text.toString()) }
                .setNegativeButton("Cancel") { _, _ -> h.cancel() }
                .setOnCancelListener { h.cancel() }
                .show()
        }

        override fun onRenderProcessGone(v: WebView, d: RenderProcessGoneDetail): Boolean {
            toast("The tab ran out of memory and was closed")
            closeTab(t)
            return true
        }
    }

    private fun chrome(t: Tab) = object : WebChromeClient() {
        override fun onProgressChanged(v: WebView, p: Int) {
            if (t !== cur) return
            progress.setProgress(p, true)
            progress.visibility = if (p in 1..99 && !isHome(t.url)) View.VISIBLE else View.GONE
            val l = p < 100
            if (l != t.loading) { t.loading = l; updateAction() }
        }

        override fun onReceivedTitle(v: WebView, title: String?) { t.title = title ?: "" }

        override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
            if (t.console.size >= 400) t.console.removeAt(0)
            val src = cm.sourceId().orEmpty().substringAfterLast('/').let { if (it.isEmpty()) "" else "$it:${cm.lineNumber()}" }
            t.console.add(DevTools.Entry(cm.messageLevel().name, cm.message().orEmpty(), src))
            return true
        }
        override fun onReceivedIcon(v: WebView, icon: Bitmap?) { t.icon = icon }

        override fun onCreateWindow(v: WebView, isDialog: Boolean, userGesture: Boolean, msg: Message): Boolean {
            if (cfg.blockPopups && !userGesture) { toast("Pop-up blocked"); return false }
            val nt = Tab(t.secret)
            nt.url = "about:blank"
            nt.desktop = t.desktop
            tabs.add(nt)
            val w = createWeb(nt)
            (msg.obj as WebView.WebViewTransport).webView = w
            msg.sendToTarget()
            select(nt)
            return true
        }

        override fun onCloseWindow(w: WebView) { tabs.firstOrNull { it.web === w }?.let { closeTab(it) } }

        override fun onShowCustomView(view: View, cb: CustomViewCallback) {
            customView = view; customCb = cb
            fullscreen.addView(view)
            fullscreen.visibility = View.VISIBLE
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        override fun onHideCustomView() { hideCustom() }

        override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
            fileCb?.onReceiveValue(null)
            fileCb = cb
            return try { picker.launch(p.createIntent()); true } catch (e: Exception) { fileCb = null; false }
        }

        override fun onPermissionRequest(req: PermissionRequest) {
            runOnUiThread {
                val grantable = req.resources.filter {
                    it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE ||
                        it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
                }.toTypedArray()
                val need = ArrayList<String>()
                val names = ArrayList<String>()
                if (grantable.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) { need.add(Manifest.permission.CAMERA); names.add("camera") }
                if (grantable.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) { need.add(Manifest.permission.RECORD_AUDIO); names.add("microphone") }
                if (grantable.isEmpty()) { req.deny(); return@runOnUiThread }
                if (need.isEmpty()) { req.grant(grantable); return@runOnUiThread }
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(hostOf(req.origin.toString()))
                    .setMessage("Allow this site to use your ${names.joinToString(" and ")}?")
                    .setPositiveButton("Allow") { _, _ -> askPerms(need) { ok -> if (ok) req.grant(grantable) else req.deny() } }
                    .setNegativeButton("Block") { _, _ -> req.deny() }
                    .setOnCancelListener { req.deny() }
                    .show()
            }
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, cb: GeolocationPermissions.Callback) {
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(hostOf(origin))
                .setMessage("Allow this site to know your location?")
                .setPositiveButton("Allow") { _, _ ->
                    askPerms(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) { ok -> cb.invoke(origin, ok, false) }
                }
                .setNegativeButton("Block") { _, _ -> cb.invoke(origin, false, false) }
                .setOnCancelListener { cb.invoke(origin, false, false) }
                .show()
        }

        override fun onJsAlert(v: WebView, url: String, message: String, r: JsResult): Boolean {
            MaterialAlertDialogBuilder(this@MainActivity).setTitle(hostOf(url)).setMessage(message)
                .setPositiveButton("OK") { _, _ -> r.confirm() }.setOnCancelListener { r.cancel() }.show()
            return true
        }

        override fun onJsConfirm(v: WebView, url: String, message: String, r: JsResult): Boolean {
            MaterialAlertDialogBuilder(this@MainActivity).setTitle(hostOf(url)).setMessage(message)
                .setPositiveButton("OK") { _, _ -> r.confirm() }
                .setNegativeButton("Cancel") { _, _ -> r.cancel() }
                .setOnCancelListener { r.cancel() }.show()
            return true
        }

        override fun onJsPrompt(v: WebView, url: String, message: String, def: String?, r: JsPromptResult): Boolean {
            val input = EditText(this@MainActivity).apply { setText(def ?: ""); setSingleLine() }
            val box = FrameLayout(this@MainActivity).apply { setPadding(dp(24), dp(8), dp(24), 0); addView(input) }
            MaterialAlertDialogBuilder(this@MainActivity).setTitle(hostOf(url)).setMessage(message).setView(box)
                .setPositiveButton("OK") { _, _ -> r.confirm(input.text.toString()) }
                .setNegativeButton("Cancel") { _, _ -> r.cancel() }
                .setOnCancelListener { r.cancel() }.show()
            return true
        }
    }

    private fun hideCustom() {
        fullscreen.removeAllViews()
        fullscreen.visibility = View.GONE
        customCb?.onCustomViewHidden()
        customView = null; customCb = null
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    private fun readAsset(name: String): String = assets.open(name).bufferedReader().use { it.readText() }

    private fun jsq(x: String): String = JSONObject.quote(x).replace("</", "<\\/")

    private fun isOnline(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return true
        val n = cm.activeNetwork ?: return false
        val c = cm.getNetworkCapabilities(n) ?: return false
        return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showOffline(v: WebView, t: Tab, url: String) {
        t.upgradedFrom = null
        t.offlineUrl = url
        val page = readAsset("offline.html")
            .replace("{{HOST}}", TextUtils.htmlEncode(hostOf(url)))
            .replace("{{TARGET}}", jsq(url))
            .replace("{{HOME}}", jsq(homeUrl(t.secret)))
        v.loadDataWithBaseURL(url, page, "text/html", "utf-8", url)
    }

    private fun showError(v: WebView, t: Tab, url: String, msg: String, code: Int) {
        val http = t.upgradedFrom
        t.upgradedFrom = null
        t.offlineUrl = null
        val (title, hint) = when (code) {
            WebViewClient.ERROR_HOST_LOOKUP -> "Server not found" to "Check the address for typing mistakes, or try again later."
            WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT -> "Couldn't connect" to "The site took too long to respond or refused the connection."
            WebViewClient.ERROR_FAILED_SSL_HANDSHAKE, WebViewClient.ERROR_UNSUPPORTED_SCHEME -> "Connection isn't secure" to "The site's security certificate isn't trusted, so SiLu stopped loading it."
            WebViewClient.ERROR_TOO_MANY_REQUESTS, WebViewClient.ERROR_REDIRECT_LOOP -> "Too many redirects" to "The site keeps sending you in circles. Clearing its cookies may help."
            else -> "Can't open this page" to "Something went wrong while loading it."
        }
        val httpBtn = if (http != null) "<button class=\"ghost\" id=\"http\">Open HTTP version</button>" else ""
        val page = readAsset("error.html")
            .replace("{{TITLE}}", TextUtils.htmlEncode(title))
            .replace("{{HINT}}", TextUtils.htmlEncode(hint))
            .replace("{{HOST}}", TextUtils.htmlEncode(hostOf(url)))
            .replace("{{CODE}}", TextUtils.htmlEncode(msg.ifBlank { "Error $code" } + "  ($code)"))
            .replace("{{HTTP_BTN}}", httpBtn)
            .replace("{{TARGET}}", jsq(url))
            .replace("{{HOME}}", jsq(homeUrl(t.secret)))
            .replace("{{HTTP}}", jsq(http ?: ""))
        v.loadDataWithBaseURL(url, page, "text/html", "utf-8", url)
    }

    private var netCb: ConnectivityManager.NetworkCallback? = null

    private fun watchNetwork(on: Boolean) {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        try {
            if (on && netCb == null) {
                val cb = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        runOnUiThread {
                            tabs.filter { it.offlineUrl != null && it.web?.title == OFFLINE_TITLE }.forEach { tab ->
                                val u = tab.offlineUrl ?: return@forEach
                                tab.offlineUrl = null
                                tab.web?.loadUrl(u)
                            }
                        }
                    }
                }
                cm.registerDefaultNetworkCallback(cb)
                netCb = cb
            } else if (!on) {
                netCb?.let { cm.unregisterNetworkCallback(it) }
                netCb = null
            }
        } catch (e: Exception) { }
    }

    private fun placeAddr() {
        val target = if (cfg.barBottom) bottomSlot else topSlot
        suggestList.gravity = if (cfg.barBottom) Gravity.BOTTOM else Gravity.TOP
        topSlot.background = if (cfg.barBottom) null else ContextCompat.getDrawable(this, R.drawable.bg_bar_top)
        if (addrRow.parent === target) return
        (addrRow.parent as? ViewGroup)?.removeView(addrRow)
        target.addView(addrRow, FrameLayout.LayoutParams(MATCH, WRAP))
    }

    private fun focusPill(focus: Boolean) {
        val sec = cur?.secret == true
        val base = col(if (sec) R.color.secret_bg else R.color.pill)
        val ring = col(if (sec) R.color.secret else R.color.accent)
        pill.background = when {
            focus -> rounded(base, 28, ring, 2)
            sec -> rounded(base, 26, withAlpha(col(R.color.secret), 70), 1)
            else -> ContextCompat.getDrawable(this, R.drawable.bg_pill)
        }
    }

    private var barAnim: android.animation.ValueAnimator? = null
    private var barFrac = 0f
    private var topFullH = 0
    private var botFullH = 0

    private fun fullHeight(v: View): Int {
        v.measure(View.MeasureSpec.makeMeasureSpec(root.width.coerceAtLeast(1), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        return v.measuredHeight
    }

    private fun applyBars(f: Float) {
        barFrac = f
        fun fit(v: View, full: Int, dir: Int) {
            val lp = v.layoutParams
            val h = if (f <= 0f) ViewGroup.LayoutParams.WRAP_CONTENT else (full * (1f - f)).toInt()
            if (lp.height != h) { lp.height = h; v.layoutParams = lp }
            val g = v as ViewGroup
            for (i in 0 until g.childCount) g.getChildAt(i).apply {
                translationY = dir * full * f
                alpha = 1f - f * .6f
            }
        }
        fit(topSlot, topFullH, -1)
        fit(bottomBar, botFullH, 1)
    }

    private fun setBars(show: Boolean, force: Boolean = false) {
        if (show == barsShown && !force) return
        barsShown = show
        lastToggle = System.currentTimeMillis()
        scrollAcc = 0
        barAnim?.cancel()
        val to = if (show) 0f else 1f
        if (barFrac <= 0f) { topFullH = fullHeight(topSlot); botFullH = fullHeight(bottomBar) }
        else if (topFullH == 0 && botFullH == 0) { topFullH = fullHeight(topSlot); botFullH = fullHeight(bottomBar) }
        if (force || !topSlot.isLaidOut || barFrac == to) { applyBars(to); return }
        barAnim = android.animation.ValueAnimator.ofFloat(barFrac, to).apply {
            duration = if (show) 280L else 240L
            interpolator = Motion.soft
            addUpdateListener { applyBars(it.animatedValue as Float) }
            start()
        }
    }

    private fun onWebScroll(dy: Int, y: Int) {
        if (!cfg.hideOnScroll || urlBox.hasFocus() || findBar.visibility == View.VISIBLE) return
        if (System.currentTimeMillis() - lastToggle < 400) return
        if (y < dp(16)) { setBars(true); return }
        if ((dy > 0) != (scrollAcc > 0)) scrollAcc = 0
        scrollAcc += dy
        if (scrollAcc > dp(56)) setBars(false) else if (scrollAcc < -dp(32)) setBars(true)
    }

    private fun updateAction() {
        val t = cur
        val res: Int
        val desc: String
        if (urlBox.hasFocus()) {
            if (urlBox.text.isNotEmpty()) { res = R.drawable.ic_clear; desc = "Clear" }
            else { res = R.drawable.ic_mic; desc = "Voice search" }
        } else if (t?.loading == true) { res = R.drawable.ic_close; desc = "Stop loading" }
        else { res = R.drawable.ic_refresh; desc = "Reload" }
        if (actionRes != res) {
            actionRes = res
            actionBtn.setImageResource(res)
            actionBtn.scaleX = .55f; actionBtn.scaleY = .55f; actionBtn.alpha = .4f
            actionBtn.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).setInterpolator(OvershootInterpolator(1.6f)).start()
        }
        actionBtn.contentDescription = desc
        actionBtn.visibility = View.VISIBLE
    }

    private fun updateUi() {
        val t = cur ?: return
        val count = tabs.size.toString()
        if (tabCount.text.toString() != count) {
            tabCount.text = count
            tabCount.scaleX = 1.4f; tabCount.scaleY = 1.4f
            tabCount.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(OvershootInterpolator(1.6f)).start()
        }
        backBtn.alpha = if (t.web?.canGoBack() == true) 1f else .35f
        fwdBtn.alpha = if (t.web?.canGoForward() == true) 1f else .35f
        if (t.secret != lastSecretUi) {
            lastSecretUi = t.secret
            pill.background = if (t.secret) rounded(col(R.color.secret_bg), 26, withAlpha(col(R.color.secret), 70), 1) else ContextCompat.getDrawable(this, R.drawable.bg_pill)
            urlBox.setTextColor(if (t.secret) 0xFFEDE9FF.toInt() else col(R.color.text))
            urlBox.setHintTextColor(if (t.secret) 0xFFB8B0E8.toInt() else col(R.color.sub))
            secretIcon.visibility = if (t.secret) View.VISIBLE else View.GONE
        }
        if (!urlBox.hasFocus()) {
            setBox(if (isHome(t.url)) "" else hostOf(t.url))
            lock.visibility = if (t.url.startsWith("https:")) View.VISIBLE else View.GONE
        }
        updateAction()
    }

    private fun localSuggestions(q: String): List<Sugg> {
        val out = ArrayList<Sugg>()
        val seen = HashSet<String>()
        fun scan(list: List<Entry>, kind: Int, limit: Int) {
            var n = 0
            for (i in list.indices.reversed()) {
                if (n >= limit) break
                val e = list[i]
                if (e.url in seen) continue
                if (q.isNotEmpty() && !e.url.contains(q, true) && !e.title.contains(q, true)) continue
                seen.add(e.url)
                out.add(Sugg(kind, e.title.ifBlank { hostOf(e.url) }, e.url))
                n++
            }
        }
        scan(Store.bookmarks, 2, 3)
        if (cur?.secret != true) scan(Store.history, 1, if (q.isEmpty()) 6 else 4)
        return out
    }

    private fun onQueryChanged(raw: String) {
        val q = raw.trim()
        val unchanged = q.isEmpty() || q == cur?.url
        val local = localSuggestions(if (unchanged) "" else q)
        renderSuggest(if (unchanged) "" else q, local, emptyList())
        suggestSeq++
        val seq = suggestSeq
        ui.removeCallbacksAndMessages(suggestToken)
        val tmpl = cfg.engine().suggest
        if (unchanged || !cfg.suggestions || tmpl == null) return
        ui.postAtTime({ fetchSuggest(tmpl, q, seq, local) }, suggestToken, SystemClock.uptimeMillis() + 180)
    }

    private fun fetchSuggest(tmpl: String, q: String, seq: Int, local: List<Sugg>) {
        io.execute {
            try {
                val c = (URL(tmpl.replace("%s", Uri.encode(q))).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000; readTimeout = 3000
                }
                val body = c.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(body).getJSONArray(1)
                val list = (0 until arr.length()).map { arr.getString(it) }.take(6)
                runOnUiThread { if (seq == suggestSeq && urlBox.hasFocus()) renderSuggest(q, local, list) }
            } catch (e: Exception) { }
        }
    }

    private fun renderSuggest(q: String, local: List<Sugg>, remote: List<String>) {
        val items = ArrayList<Sugg>()
        if (q.isNotEmpty()) items.add(Sugg(if (SCHEME_RE.matches(q) || (!q.contains(' ') && URL_RE.matches(q))) 3 else 0, q))
        items.addAll(local.take(3))
        remote.filter { it != q }.take(5).forEach { items.add(Sugg(0, it)) }
        suggestList.removeAllViews()
        (if (cfg.barBottom) items.reversed() else items).forEach { suggestList.addView(suggRow(it)) }
        if (items.isEmpty()) { suggestScroll.animate().cancel(); suggestScroll.visibility = View.GONE }
        else if (suggestScroll.visibility != View.VISIBLE) {
            suggestScroll.alpha = 0f
            suggestScroll.visibility = View.VISIBLE
            suggestScroll.animate().alpha(1f).setDuration(200).setInterpolator(Motion.enter).start()
        }
    }

    private fun suggRow(s: Sugg): View {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(54)
            setPadding(dp(16), 0, dp(6), 0)
            background = rippleRounded(Color.TRANSPARENT, 18)
            setOnClickListener { navigate(s.url ?: normalize(s.text)) }
        }
        val icon = when (s.kind) { 1 -> R.drawable.ic_history; 2 -> R.drawable.ic_star; 3 -> R.drawable.ic_globe; else -> R.drawable.ic_search }
        r.addView(ImageView(this).apply { setImageResource(icon); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(20), dp(20)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = s.text; setTextColor(col(R.color.text)); textSize = 15f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        if (s.url != null) texts.addView(TextView(this).apply {
            text = hostOf(s.url); setTextColor(col(R.color.sub)); textSize = 12f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        r.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(16) })
        r.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_ul)
            background = selectable()
            contentDescription = "Use this suggestion"
            setOnClickListener { urlBox.setText(s.url ?: s.text); urlBox.setSelection(urlBox.text.length) }
        }, LinearLayout.LayoutParams(dp(46), dp(46)))
        return r
    }

    private fun showMenu() {
        val t = cur ?: return
        releaseFocus()
        popup?.dismiss()
        val w = dp(288)
        val home = isHome(t.url)
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }

        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun q(icon: Int, label: String, enabled: Boolean = true, onClick: () -> Unit) {
            val b = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                background = rippleRounded(Color.TRANSPARENT, 18)
                alpha = if (enabled) 1f else .35f
                contentDescription = label
                setOnClickListener { if (enabled) { popup?.dismiss(); onClick() } }
                pressable()
            }
            b.addView(ImageView(this).apply { setImageResource(icon); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(22), dp(22)))
            b.addView(TextView(this).apply { text = label; textSize = 11f; setTextColor(col(R.color.sub)); gravity = Gravity.CENTER; setPadding(0, dp(5), 0, 0) })
            quick.addView(b, LinearLayout.LayoutParams(0, dp(66), 1f))
        }
        q(R.drawable.ic_forward, "Forward", t.web?.canGoForward() == true) { t.web?.goForward() }
        q(if (Store.isBookmarked(t.url)) R.drawable.ic_star_fill else R.drawable.ic_star, "Bookmark", !home) {
            toast(if (Store.toggleBookmark(t.title.ifBlank { hostOf(t.url) }, t.url)) "Bookmark added" else "Bookmark removed")
        }
        q(R.drawable.ic_share, "Share", !home) { share(t.url) }
        q(R.drawable.ic_copy, "Copy link", !home) { copy(t.url) }
        card.addView(quick)

        fun divider() {
            card.addView(View(this).apply { setBackgroundColor(col(R.color.divider)) },
                LinearLayout.LayoutParams(MATCH, 1).apply { setMargins(dp(10), dp(6), dp(10), dp(6)) })
        }
        fun item(icon: Int, label: String, checked: Boolean = false, sub: String? = null, onClick: () -> Unit) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(50); setPadding(dp(12), dp(4), dp(12), dp(4))
                background = rippleRounded(Color.TRANSPARENT, 16)
                setOnClickListener { popup?.dismiss(); onClick() }
            }
            r.addView(ImageView(this).apply { setImageResource(icon); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(22), dp(22)))
            val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            c.addView(TextView(this).apply { text = label; setTextColor(col(R.color.text)); textSize = 15f })
            if (sub != null) c.addView(TextView(this).apply { text = sub; setTextColor(col(R.color.sub)); textSize = 12f })
            r.addView(c, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(14) })
            if (checked) r.addView(ImageView(this).apply { setImageResource(R.drawable.ic_check) }, LinearLayout.LayoutParams(dp(20), dp(20)))
            card.addView(r)
        }

        divider()
        item(R.drawable.ic_plus, "New tab") { addTab(null) }
        item(R.drawable.ic_incognito, "New secret tab") { addTab(null, true) }
        if (closed.isNotEmpty()) item(R.drawable.ic_undo, "Reopen closed tab") { reopenClosed() }
        divider()
        item(R.drawable.ic_star, "Bookmarks") { showBookmarks() }
        item(R.drawable.ic_history, "History") { showHistory() }
        item(R.drawable.ic_download, "Downloads") { showDownloads() }
        divider()
        item(R.drawable.ic_desktop, "Desktop site", t.desktop) {
            t.desktop = !t.desktop
            applyTabSettings(t)
            t.web?.reload()
        }
        item(R.drawable.ic_search, "Find in page") { openFind() }
        if (!home) {
            item(R.drawable.ic_translate, "Translate page") { translatePage(t) }
            item(R.drawable.ic_print, "Print / Save as PDF") { printPage(t) }
            val host = hostOf(t.url)
            val on = !cfg.isAllowed(host)
            item(R.drawable.ic_shield, "Ad & tracker blocking", on && (adsOn || trkOn), "${t.blocked.get()} blocked on this page") {
                cfg.setAllowed(host, on)
                t.allow = on
                t.web?.reload()
                toast(if (on) "Blocking off for $host" else "Blocking on for $host")
            }
            item(R.drawable.ic_home, "Add to home shortcuts") { Store.addShortcut(t.title.ifBlank { host }, t.url); toast("Added to home") }
        }
        card.addView(zoomRow(t))
        divider()
        if (!home) item(R.drawable.ic_terminal, "Developer tools") { DevTools(this, t).show() }
        item(R.drawable.ic_code, "Userscripts") { showUserscripts() }
        item(R.drawable.ic_sliders, "Settings") { openSettings() }

        val scroll = ScrollView(this).apply {
            background = rounded(col(R.color.surface), 26, col(R.color.divider), 1)
            clipToOutline = true
            elevation = dp(10).toFloat()
            isVerticalScrollBarEnabled = false
            addView(card)
        }
        val wrap = FrameLayout(this).apply { setPadding(dp(12), dp(8), dp(12), dp(10)); addView(scroll) }
        val pw = w + dp(24)
        wrap.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((resources.displayMetrics.heightPixels * 0.74).toInt(), View.MeasureSpec.AT_MOST))
        val h = wrap.measuredHeight
        val pop = PopupWindow(wrap, pw, h, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            animationStyle = R.style.Silu_PopupAnim
        }
        popup = pop
        val loc = IntArray(2)
        menuBtn.getLocationOnScreen(loc)
        val xoff = resources.displayMetrics.widthPixels - pw - loc[0]
        pop.showAsDropDown(menuBtn, xoff, -(h + menuBtn.height))
    }

    private fun zoomRow(t: Tab): View {
        val h = hostOf(t.url)
        val label = TextView(this).apply { textSize = 14f; setTextColor(col(R.color.text)); gravity = Gravity.CENTER; minWidth = dp(54) }
        fun zoomNow() = cfg.siteZoom(h) ?: cfg.textZoom
        fun refresh() { label.text = "${zoomNow()}%" }
        fun step(d: Int) {
            val v = (zoomNow() + d).coerceIn(50, 200)
            cfg.setSiteZoom(h, v)
            t.web?.settings?.textZoom = v
            refresh()
        }
        fun stepBtn(text: String, desc: String, d: Int) = TextView(this).apply {
            this.text = text; textSize = 16f; gravity = Gravity.CENTER; setTextColor(col(R.color.text))
            background = rippleRounded(col(R.color.pill), 18); contentDescription = desc
            setOnClickListener { step(d) }
            pressable()
        }
        refresh()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(54); setPadding(dp(12), dp(4), dp(12), dp(4))
            addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.ic_text); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(TextView(this@MainActivity).apply { text = "Text size"; setTextColor(col(R.color.text)); textSize = 15f },
                LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(14) })
            addView(stepBtn("A−", "Smaller text", -10), LinearLayout.LayoutParams(dp(42), dp(38)))
            addView(label)
            addView(stepBtn("A+", "Larger text", 10), LinearLayout.LayoutParams(dp(42), dp(38)))
        }
    }

    private fun openSettings(page: String? = null) {
        val i = Intent(this, SettingsActivity::class.java)
        if (page != null) i.putExtra("page", page)
        startActivity(i)
        overridePendingTransition(R.anim.slide_in_right, R.anim.hold)
    }

    private fun snack(msg: String, action: String? = null, onAction: (() -> Unit)? = null) {
        val sb = Snackbar.make(root, msg, Snackbar.LENGTH_LONG)
        sb.setAnchorView(bottomBar)
        sb.view.background = rounded(col(R.color.pill), 20, col(R.color.divider), 1)
        sb.setTextColor(col(R.color.text))
        if (action != null && onAction != null) {
            sb.setAction(action) { onAction() }
            sb.setActionTextColor(col(R.color.accent))
        }
        sb.show()
    }

    private fun reopenClosed() {
        val u = closed.removeLastOrNull() ?: return
        addTab(u)
    }

    private fun translatePage(t: Tab) {
        val l = Locale.getDefault()
        val tag = l.toLanguageTag()
        var lang = tag.substringBefore('-')
        if (lang == "zh") lang = if (tag.contains("Hant") || l.country in setOf("TW", "HK", "MO")) "zh-TW" else "zh-CN"
        navigate("https://translate.google.com/translate?sl=auto&tl=" + lang + "&u=" + Uri.encode(t.url))
    }

    private fun printPage(t: Tab) {
        val w = t.web ?: return
        val name = t.title.ifBlank { hostOf(t.url) }
        try {
            (getSystemService(PRINT_SERVICE) as PrintManager).print(name, w.createPrintDocumentAdapter(name), PrintAttributes.Builder().build())
        } catch (e: Exception) { toast("Couldn't start printing") }
    }

    private fun startVoice() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search")
        try { voice.launch(i) } catch (e: Exception) { toast("Voice search isn't available on this device") }
    }

    private fun openFind() {
        findBar.alpha = 0f
        findBar.visibility = View.VISIBLE
        findBar.animate().alpha(1f).setDuration(220).setInterpolator(Motion.enter).start()
        setBars(true, force = true)
        findInput.requestFocus()
        WindowCompat.getInsetsController(window, findInput).show(WindowInsetsCompat.Type.ime())
    }

    private fun closeFind() {
        cur?.web?.clearMatches()
        findInput.setText("")
        findCount.text = ""
        findBar.animate().alpha(0f).setDuration(160).setInterpolator(Motion.exit).withEndAction { findBar.visibility = View.GONE }.start()
        releaseFocus()
    }

    private fun showTabs() {
        releaseFocus()
        popup?.dismiss()
        cur?.let { capture(it) }
        showSecretTabs = cur?.secret == true
        segAnim?.cancel()
        tabsGrid.animate().cancel(); tabsGrid.alpha = 1f; tabsGrid.translationX = 0f
        tabsTitle.animate().cancel(); tabsTitle.alpha = 1f
        tabsOverlay.animate().cancel()
        tabsOverlay.visibility = View.VISIBLE
        tabsOverlay.alpha = 0f; tabsOverlay.scaleX = .94f; tabsOverlay.scaleY = .94f
        tabsOverlay.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(Motion.enter).start()
        renderTabs(true)
    }

    private fun closeTabs() {
        tabsOverlay.animate().cancel()
        tabsOverlay.animate().alpha(0f).scaleX(.95f).scaleY(.95f).setDuration(210).setInterpolator(Motion.exit)
            .withEndAction { tabsOverlay.visibility = View.GONE; updateSecure() }.start()
    }

    private var segPill: SegPillDrawable? = null
    private var segAnim: android.animation.ValueAnimator? = null

    private fun segPillDrawable(): SegPillDrawable {
        segPill?.let { return it }
        val pill = segTabs.parent as View
        val d = SegPillDrawable(pill.background, dp(4).toFloat())
        pill.background = d
        segPill = d
        return d
    }

    private fun paintSeg(pos: Float) {
        val ev = android.animation.ArgbEvaluator()
        val d = segPillDrawable()
        d.pos = pos
        d.color = ev.evaluate(pos, col(R.color.surface), col(R.color.secret_bg)) as Int
        segTabs.setTextColor(ev.evaluate(pos, col(R.color.text), col(R.color.sub)) as Int)
        segSecret.setTextColor(ev.evaluate(pos, col(R.color.sub), col(R.color.secret)) as Int)
    }

    private fun setSegment(secret: Boolean, animate: Boolean) {
        val to = if (secret) 1f else 0f
        val d = segPillDrawable()
        if (!animate) {
            if (segAnim?.isRunning == true) return
            paintSeg(to)
            return
        }
        segAnim?.cancel()
        segAnim = android.animation.ValueAnimator.ofFloat(d.pos, to).apply {
            duration = 300
            interpolator = Motion.enter
            addUpdateListener { paintSeg(it.animatedValue as Float) }
            start()
        }
    }

    private fun switchTabsSegment(secret: Boolean) {
        if (showSecretTabs == secret) return
        showSecretTabs = secret
        updateSecure()
        setSegment(secret, true)
        val dx = dp(32).toFloat() * (if (secret) -1f else 1f)
        tabsGrid.animate().cancel()
        tabsTitle.animate().cancel()
        tabsTitle.animate().alpha(0f).setDuration(120).start()
        tabsGrid.animate().alpha(0f).translationX(dx).setDuration(130).setInterpolator(Motion.exit).withEndAction {
            renderTabs()
            tabsGrid.translationX = -dx
            tabsGrid.animate().alpha(1f).translationX(0f).setDuration(300).setInterpolator(Motion.enter).start()
            tabsTitle.animate().alpha(1f).setDuration(300).start()
        }.start()
    }

    private fun renderTabs(animate: Boolean = false) {
        val list = tabs.filter { it.secret == showSecretTabs }
        tabsTitle.text = ("${list.size} — " + if (showSecretTabs) "Secret tabs" else "Tabs")
        setSegment(showSecretTabs, false)
        tabsGrid.removeAllViews()
        if (list.isEmpty()) {
            tabsGrid.addView(TextView(this).apply {
                text = if (showSecretTabs) "No secret tabs open.\nTap + to start one." else "No tabs open."
                gravity = Gravity.CENTER; setTextColor(col(R.color.sub)); setPadding(0, dp(96), 0, 0)
            })
        }
        val cards = ArrayList<View>()
        var row: LinearLayout? = null
        list.forEachIndexed { i, t ->
            if (i % 2 == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                tabsGrid.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
            }
            val c = tabCard(t)
            cards.add(c)
            row!!.addView(c, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })
        }
        if (list.size % 2 == 1) row!!.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })
        if (animate) cards.forEachIndexed { i, v ->
            v.alpha = 0f; v.translationY = dp(24).toFloat(); v.scaleX = .96f; v.scaleY = .96f
            v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setStartDelay(40L * minOf(i, 6)).setDuration(340)
                .setInterpolator(Motion.enter).start()
        }
        updateSecure()
    }

    private fun tabCard(t: Tab): View {
        val home = isHome(t.url)
        val accent = if (t.secret) col(R.color.secret) else col(R.color.accent)
        val label = if (home) (if (t.secret) "New secret tab" else "Home") else t.title.ifBlank { hostOf(t.url) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(if (t.secret) col(R.color.secret_bg) else col(R.color.surface), 24, if (t === cur) accent else col(R.color.divider), if (t === cur) 2 else 1)
            setPadding(dp(10), dp(8), dp(10), dp(10))
            contentDescription = label
            setOnClickListener { closeTabs(); select(t) }
            pressable()
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val ic = t.icon
        head.addView(ImageView(this).apply {
            if (ic != null && !home) setImageBitmap(ic) else setImageResource(if (t.secret) R.drawable.ic_incognito else if (home) R.drawable.ic_home else R.drawable.ic_globe)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(16), dp(16)))
        head.addView(TextView(this).apply {
            text = label; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; textSize = 13f
            setTextColor(col(R.color.text)); setPadding(dp(8), 0, dp(4), 0)
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        head.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_close); background = rippleRounded(Color.TRANSPARENT, 18); contentDescription = "Close tab"
            setOnClickListener {
                card.animate().alpha(0f).scaleX(.9f).scaleY(.9f).setDuration(180).setInterpolator(Motion.exit)
                    .withEndAction { closeTab(t); renderTabs() }.start()
            }
        }, LinearLayout.LayoutParams(dp(36), dp(36)))
        card.addView(head)
        val thumb = ImageView(this).apply {
            background = rounded(if (t.secret) col(R.color.chrome) else col(R.color.pill), 18)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val b = t.thumb
        if (b != null) {
            thumb.scaleType = ImageView.ScaleType.MATRIX
            thumb.setImageBitmap(b)
            thumb.post { val sc = thumb.width.toFloat() / b.width; thumb.imageMatrix = Matrix().apply { setScale(sc, sc) } }
        } else {
            thumb.scaleType = ImageView.ScaleType.CENTER
            thumb.setImageResource(if (t.secret) R.drawable.ic_incognito else R.drawable.ic_home)
        }
        card.addView(thumb, LinearLayout.LayoutParams(MATCH, dp(190)).apply { topMargin = dp(6) })
        return card
    }

    private fun showPage(title: String, kind: String, keepScroll: Boolean = false, build: (LinearLayout) -> Unit) {
        releaseFocus()
        popup?.dismiss()
        val sv = pageList.parent as ScrollView
        val y = if (keepScroll) sv.scrollY else 0
        pageKind = kind
        pageTitle.text = title
        pageList.removeAllViews()
        build(pageList)
        if (pageOverlay.visibility != View.VISIBLE) {
            pageOverlay.animate().cancel()
            pageOverlay.visibility = View.VISIBLE
            pageOverlay.alpha = 0f
            pageOverlay.translationX = dp(40).toFloat()
            pageOverlay.animate().alpha(1f).translationX(0f).setDuration(320).setInterpolator(Motion.enter).start()
        }
        if (y > 0) sv.post { sv.scrollTo(0, y) }
    }

    private fun closePage() {
        pageKind = ""
        pageOverlay.animate().cancel()
        pageOverlay.animate().alpha(0f).translationX(dp(40).toFloat()).setDuration(230).setInterpolator(Motion.exit)
            .withEndAction { pageOverlay.visibility = View.GONE }.start()
    }

    private fun note(text: String) = TextView(this).apply {
        this.text = text; setTextColor(col(R.color.sub)); gravity = Gravity.CENTER; setPadding(dp(16), dp(56), dp(16), dp(16))
    }

    private fun header(text: String) = TextView(this).apply {
        this.text = text; setTextColor(col(R.color.sub)); textSize = 13f; setPadding(dp(8), dp(16), dp(8), dp(8))
    }

    private fun row(label: String, sub: String? = null, icon: Int? = null, onClose: (() -> Unit)? = null, onClick: () -> Unit): View {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64); setPadding(dp(12), dp(8), dp(8), dp(8))
            background = rippleRounded(col(R.color.surface), 22)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(8) }
            setOnClickListener { onClick() }
        }
        if (icon != null) {
            val chip = FrameLayout(this).apply { background = rounded(col(R.color.pill), 16) }
            chip.addView(ImageView(this).apply { setImageResource(icon); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
                FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
            r.addView(chip, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(14) })
        }
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(TextView(this).apply { text = label; setTextColor(col(R.color.text)); textSize = 15f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        if (sub != null) c.addView(TextView(this).apply { text = sub; setTextColor(col(R.color.sub)); textSize = 12f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        r.addView(c, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (onClose != null) r.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_close); background = rippleRounded(Color.TRANSPARENT, 20); contentDescription = "Remove"
            setOnClickListener { onClose() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        return r
    }

    private fun dayLabel(ts: Long): String = when {
        DateUtils.isToday(ts) -> "Today"
        DateUtils.isToday(ts + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
        else -> DateUtils.formatDateTime(this, ts, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL)
    }

    private fun showHistory() {
        showPage("History", "history") { box ->
            box.addView(row("Clear browsing data…", "History, cookies, cache and more", R.drawable.ic_trash) {
                openSettings("clear")
            })
            val h = Store.history
            if (h.isEmpty()) box.addView(note("No history yet"))
            var lastDay = ""
            for (i in h.indices.reversed().take(150)) {
                val e = h[i]
                val d = dayLabel(e.ts)
                if (d != lastDay) { box.addView(header(d)); lastDay = d }
                box.addView(row(e.title.ifBlank { hostOf(e.url) }, hostOf(e.url), R.drawable.ic_globe,
                    onClose = { Store.removeHistory(e.url); showHistory() }) { closePage(); navigate(e.url) })
            }
        }
    }

    private fun showBookmarks() {
        showPage("Bookmarks", "bookmarks") { box ->
            if (Store.bookmarks.isEmpty()) box.addView(note("No bookmarks yet.\nOpen a page and tap the star in the menu."))
            for (e in Store.bookmarks.reversed()) {
                box.addView(row(e.title.ifBlank { hostOf(e.url) }, hostOf(e.url), R.drawable.ic_star,
                    onClose = { Store.removeBookmark(e.url); showBookmarks() }) { closePage(); navigate(e.url) })
            }
        }
    }

    private fun onDownload(t: Tab, url: String, ua: String?, cd: String?, mime: String?, len: Long) {
        if (!url.startsWith("http")) { toast("This type of download isn't supported yet"); return }
        val name = URLUtil.guessFileName(url, cd, mime)
        if (!cfg.askDownload) { enqueue(t, url, ua, mime, name); return }
        val input = EditText(this).apply {
            setText(name); setSingleLine()
            setSelection(0, name.substringBeforeLast('.', name).length)
        }
        val box = FrameLayout(this).apply { setPadding(dp(24), dp(8), dp(24), 0); addView(input) }
        val size = if (len > 0) Formatter.formatShortFileSize(this, len) else "Unknown size"
        MaterialAlertDialogBuilder(this)
            .setTitle("Download file?")
            .setMessage(hostOf(url) + " • " + size)
            .setView(box)
            .setPositiveButton("Download") { _, _ ->
                enqueue(t, url, ua, mime, BAD_NAME.replace(input.text.toString().trim().ifEmpty { name }, "_"))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun enqueue(t: Tab, url: String, ua: String?, mime: String?, name: String) {
        val need = ArrayList<String>()
        if (Build.VERSION.SDK_INT <= 28) need.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT >= 33 && !cfg.prefs.getBoolean("asked_notif", false)) {
            cfg.prefs.edit().putBoolean("asked_notif", true).apply()
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (need.isEmpty()) { doEnqueue(t, url, ua, mime, name); return }
        askPerms(need) { ok -> if (ok || Build.VERSION.SDK_INT > 28) doEnqueue(t, url, ua, mime, name) else toast("Storage permission is required to download") }
    }

    private fun doEnqueue(t: Tab, url: String, ua: String?, mime: String?, name: String) {
        try {
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                if (!mime.isNullOrEmpty()) setMimeType(mime)
                if (ua != null) addRequestHeader("User-Agent", ua)
                cookiesFor(t).getCookie(url)?.let { addRequestHeader("Cookie", it) }
                setTitle(name)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            }
            val id = (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            if (!t.secret) Store.addDownload(Dl(id, name, url, System.currentTimeMillis()))
            toast("Downloading $name")
        } catch (e: Exception) {
            toast("Couldn't start the download")
        }
    }

    private fun showDownloads() {
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val ids = Store.downloads.map { it.id }.toLongArray()
        val status = HashMap<Long, Int>()
        val done = HashMap<Long, Long>()
        val total = HashMap<Long, Long>()
        if (ids.isNotEmpty()) dm.query(DownloadManager.Query().setFilterById(*ids))?.use { c ->
            val iId = c.getColumnIndex(DownloadManager.COLUMN_ID)
            val iSt = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val iDone = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val iTot = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                status[id] = c.getInt(iSt); done[id] = c.getLong(iDone); total[id] = c.getLong(iTot)
            }
        }
        val active = status.values.any { it == DownloadManager.STATUS_RUNNING || it == DownloadManager.STATUS_PENDING }
        val first = pageKind != "downloads"
        showPage("Downloads", "downloads", keepScroll = !first) { box ->
            box.addView(row("Open Downloads folder", null, R.drawable.ic_download) {
                try { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) } catch (e: Exception) { toast("Couldn't open Downloads") }
            })
            if (Store.downloads.isEmpty()) box.addView(note("No downloads yet"))
            for (d in Store.downloads.reversed()) {
                val st = status[d.id]
                val sub = when (st) {
                    DownloadManager.STATUS_SUCCESSFUL -> "Completed • " + Formatter.formatShortFileSize(this, total[d.id] ?: 0)
                    DownloadManager.STATUS_FAILED -> "Failed"
                    DownloadManager.STATUS_PAUSED -> "Paused"
                    DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                        val tt = total[d.id] ?: -1L
                        if (tt > 0) "Downloading • " + ((done[d.id] ?: 0L) * 100 / tt) + "%" else "Downloading…"
                    }
                    else -> "File removed"
                }
                box.addView(row(d.name, sub + " • " + hostOf(d.url), R.drawable.ic_download, onClose = {
                    MaterialAlertDialogBuilder(this).setTitle(d.name)
                        .setItems(arrayOf("Remove from list", "Delete file from device")) { _, i ->
                            if (i == 1) try { dm.remove(d.id) } catch (e: Exception) { }
                            Store.removeDownload(d.id)
                            showDownloads()
                        }.show()
                }) {
                    if (st == DownloadManager.STATUS_SUCCESSFUL) {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW)
                                .setDataAndType(dm.getUriForDownloadedFile(d.id), dm.getMimeTypeForDownloadedFile(d.id) ?: "*/*")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: ActivityNotFoundException) { toast("No app can open this file") }
                        catch (e: Exception) { toast("Couldn't open the file") }
                    }
                })
            }
        }
        if (active) ui.postDelayed({ if (pageKind == "downloads" && pageOverlay.visibility == View.VISIBLE) showDownloads() }, 1500)
    }

    private fun uploadDir() = File(cacheDir, "upload")

    private fun cleanUploads() { try { uploadDir().deleteRecursively() } catch (e: Exception) { } }

    private fun copyForUpload(uris: Array<Uri>): Array<Uri> = Array(uris.size) { i ->
        val u = uris[i]
        if (u.scheme != "content") u else try {
            var name = "file"
            contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0) ?: name
            }
            val dir = File(uploadDir(), System.nanoTime().toString()).apply { mkdirs() }
            val f = File(dir, BAD_NAME.replace(name, "_"))
            contentResolver.openInputStream(u)?.use { input -> f.outputStream().use { input.copyTo(it) } }
            if (f.exists() && f.length() > 0) Uri.fromFile(f) else u
        } catch (e: Exception) { u }
    }

    private fun onLongPress(t: Tab, web: WebView): Boolean {
        val r = web.hitTestResult
        val extra = r.extra ?: return false
        when (r.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                val isImg = r.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                val msg = Handler(Looper.getMainLooper(), Handler.Callback { m ->
                    val link = m.data.getString("url") ?: extra
                    contextSheet(t, link, m.data.getString("title"), if (isImg) (m.data.getString("src") ?: extra) else null)
                    true
                }).obtainMessage()
                web.requestFocusNodeHref(msg)
                return true
            }
            WebView.HitTestResult.IMAGE_TYPE -> { contextSheet(t, null, null, extra); return true }
            WebView.HitTestResult.PHONE_TYPE -> {
                sheet(extra) { box, d ->
                    sheetItem(box, d, R.drawable.ic_link, "Call") { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$extra"))) }
                    sheetItem(box, d, R.drawable.ic_copy, "Copy number") { copy(extra) }
                }
                return true
            }
            WebView.HitTestResult.EMAIL_TYPE -> {
                sheet(extra) { box, d ->
                    sheetItem(box, d, R.drawable.ic_link, "Send email") { startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$extra"))) }
                    sheetItem(box, d, R.drawable.ic_copy, "Copy email address") { copy(extra) }
                }
                return true
            }
        }
        return false
    }

    private fun contextSheet(t: Tab, link: String?, text: String?, image: String?) {
        sheet(link ?: image ?: "") { box, d ->
            if (link != null) {
                sheetItem(box, d, R.drawable.ic_tabs, "Open in new tab") { addTab(link, t.secret) }
                if (!t.secret && secretSupported()) sheetItem(box, d, R.drawable.ic_incognito, "Open in secret tab") { addTab(link, true) }
                sheetItem(box, d, R.drawable.ic_copy, "Copy link address") { copy(link) }
                if (!text.isNullOrBlank()) sheetItem(box, d, R.drawable.ic_text, "Copy link text") { copy(text) }
                sheetItem(box, d, R.drawable.ic_download, "Download link") { onDownload(t, link, null, null, null, -1) }
                sheetItem(box, d, R.drawable.ic_share, "Share link") { share(link) }
            }
            if (image != null) {
                if (link != null) box.addView(View(this).apply { setBackgroundColor(col(R.color.divider)) }, LinearLayout.LayoutParams(MATCH, 1).apply { setMargins(dp(16), dp(6), dp(16), dp(6)) })
                sheetItem(box, d, R.drawable.ic_image, "Open image in new tab") { addTab(image, t.secret) }
                sheetItem(box, d, R.drawable.ic_download, "Download image") { onDownload(t, image, null, null, null, -1) }
                sheetItem(box, d, R.drawable.ic_copy, "Copy image address") { copy(image) }
                sheetItem(box, d, R.drawable.ic_share, "Share image link") { share(image) }
                if (!t.secret) sheetItem(box, d, R.drawable.ic_search, "Search image with Google Lens") {
                    addTab("https://lens.google.com/uploadbyurl?url=" + Uri.encode(image))
                }
            }
        }
    }

    private fun sheet(title: String, build: (LinearLayout, BottomSheetDialog) -> Unit) {
        val d = BottomSheetDialog(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(20)) }
        box.addView(View(this).apply { background = rounded(col(R.color.sub), 2); alpha = .5f },
            LinearLayout.LayoutParams(dp(36), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(14) })
        box.addView(TextView(this).apply {
            text = title; setTextColor(col(R.color.sub)); textSize = 13f; maxLines = 2
            ellipsize = TextUtils.TruncateAt.MIDDLE; setPadding(dp(16), 0, dp(16), dp(10))
        })
        build(box, d)
        d.setContentView(ScrollView(this).apply { addView(box) })
        d.show()
    }

    private fun sheetItem(box: LinearLayout, d: BottomSheetDialog, icon: Int, label: String, onClick: () -> Unit) {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(54); setPadding(dp(16), 0, dp(16), 0)
            background = rippleRounded(Color.TRANSPARENT, 18)
            setOnClickListener { d.dismiss(); onClick() }
        }
        r.addView(ImageView(this).apply { setImageResource(icon); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(22), dp(22)))
        r.addView(TextView(this).apply { text = label; setTextColor(col(R.color.text)); textSize = 15f },
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(16) })
        box.addView(r)
    }

    private fun siluLink(u: Uri) {
        when (u.host) {
            "add" -> shortcutDialog(null) { n, url -> Store.addShortcut(n, url); refreshHome() }
            "customize" -> startActivity(Intent(this, SettingsActivity::class.java).putExtra("page", "home"))
        }
    }

    private fun refreshHome() {
        val t = cur ?: return
        if (isHome(t.url)) t.web?.loadUrl(homeUrl(t.secret))
    }

    private fun notifState(origin: String): String =
        if (!cfg.siteNotifications) "denied" else cfg.notifPerm(origin)

    private fun onBridge(data: String?, origin: String, reply: androidx.webkit.JavaScriptReplyProxy) {
        if (data == null || !origin.startsWith("http")) return
        val o = try { JSONObject(data) } catch (e: Exception) { return }
        when (o.optString("t")) {
            "state" -> reply.postMessage(JSONObject().put("t", "perm").put("v", notifState(origin)).toString())
            "perm" -> askNotif(origin) { v -> reply.postMessage(JSONObject().put("t", "perm").put("v", v).toString()) }
            "show" -> showWebNotification(origin, o)
        }
    }

    private var lastPasskeyAlert = 0L
    private var passkeyDialog: androidx.appcompat.app.AlertDialog? = null

    private fun onPasskeyAttempt() {
        runOnUiThread {
            val now = SystemClock.elapsedRealtime()
            if (isFinishing || passkeyDialog?.isShowing == true || now - lastPasskeyAlert < 3000) return@runOnUiThread
            lastPasskeyAlert = now
            passkeyDialog = MaterialAlertDialogBuilder(this)
                .setTitle("Passkeys not supported")
                .setMessage("SiLu Browser doesn't support passkeys yet. Choose \"another way\" on the site (password, code or QR code), or sign in once with Chrome.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun askNotif(origin: String, done: (String) -> Unit) {
        val st = notifState(origin)
        if (st != "default") { done(st); return }
        MaterialAlertDialogBuilder(this)
            .setTitle(hostOf(origin))
            .setMessage("This site wants to show notifications.")
            .setPositiveButton("Allow") { _, _ ->
                cfg.setNotifPerm(origin, "granted")
                if (Build.VERSION.SDK_INT >= 33) askPerms(listOf(Manifest.permission.POST_NOTIFICATIONS)) { }
                done("granted")
            }
            .setNegativeButton("Block") { _, _ -> cfg.setNotifPerm(origin, "denied"); done("denied") }
            .setOnCancelListener { done("default") }
            .show()
    }

    private fun showWebNotification(origin: String, o: JSONObject) {
        if (notifState(origin) != "granted") return
        val now = SystemClock.elapsedRealtime()
        if (now - (lastNotif[origin] ?: 0L) < 1500) return
        lastNotif[origin] = now
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(origin), this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, origin.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val body = o.optString("body").take(300)
        val n = NotificationCompat.Builder(this, SiluApp.CH_WEB)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(o.optString("title").take(100).ifBlank { hostOf(origin) })
            .setContentText(body)
            .setSubText(hostOf(origin))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try { NotificationManagerCompat.from(this).notify(o.optString("tag").ifBlank { null }, origin.hashCode(), n) } catch (e: SecurityException) { }
    }

    private fun scripts(): JSONArray = try { JSONArray(cfg.prefs.getString("us", "[]")) } catch (e: Exception) { JSONArray() }
    private fun saveScripts(a: JSONArray) = cfg.prefs.edit().putString("us", a.toString()).apply()

    private fun meta(code: String): Triple<String, List<String>, List<String>> {
        var name = ""
        val inc = mutableListOf<String>()
        val exc = mutableListOf<String>()
        val head = Regex("==UserScript==([\\s\\S]*?)==/UserScript==").find(code)?.groupValues?.get(1) ?: ""
        Regex("@(\\S+)[ \\t]+(.+)").findAll(head).forEach { m ->
            val v = m.groupValues[2].trim()
            when (m.groupValues[1]) {
                "name" -> if (name.isEmpty()) name = v
                "match", "include" -> inc.add(v)
                "exclude", "exclude-match" -> exc.add(v)
            }
        }
        return Triple(name.ifEmpty { "Untitled script" }, inc, exc)
    }

    private fun patternToRegex(p: String): Regex {
        if (p == "*" || p == "<all_urls>") return Regex(".*")
        if (p.length > 2 && p.startsWith("/") && p.endsWith("/")) return Regex(p.substring(1, p.length - 1))
        val sb = StringBuilder()
        var rest = p
        if (rest.startsWith("*://")) { sb.append("https?://"); rest = rest.substring(4) }
        if (rest.startsWith("*.")) { sb.append("(?:[^/]*\\.)?"); rest = rest.substring(2) }
        for (c in rest) if (c == '*') sb.append(".*") else sb.append(Regex.escape(c.toString()))
        return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }

    private fun injectUserscripts(web: WebView, url: String) {
        val arr = scripts()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (!o.optBoolean("on", true)) continue
            val code = o.getString("code")
            val (_, inc, exc) = meta(code)
            val ok = runCatching {
                exc.none { patternToRegex(it).matches(url) } && (inc.isEmpty() || inc.any { patternToRegex(it).matches(url) })
            }.getOrDefault(false)
            if (!ok) continue
            val id = o.getString("id")
            web.evaluateJavascript(
                "(function(){if(window.__silu_$id)return;window.__silu_$id=1;$SHIM try{\n$code\n}catch(e){console.error('[SiLu userscript]',e)}})();",
                null
            )
        }
    }

    private fun addScript(code: String) {
        if (code.isBlank()) return
        val a = scripts()
        a.put(JSONObject().put("id", System.currentTimeMillis().toString()).put("name", meta(code).first).put("code", code).put("on", true))
        saveScripts(a)
        toast("Userscript saved")
    }

    private fun updateScripts(id: String, remove: Boolean) {
        val src = scripts(); val out = JSONArray()
        for (i in 0 until src.length()) {
            val o = src.getJSONObject(i)
            if (o.getString("id") == id) { if (remove) continue; o.put("on", !o.optBoolean("on", true)) }
            out.put(o)
        }
        saveScripts(out)
    }

    private fun installUserscript(u: String) {
        io.execute {
            try {
                val c = (URL(u).openConnection() as HttpURLConnection).apply { connectTimeout = 10000; readTimeout = 10000 }
                val code = c.inputStream.bufferedReader().use { it.readText() }
                runOnUiThread {
                    MaterialAlertDialogBuilder(this).setTitle("Install userscript?")
                        .setMessage(meta(code).first + "\n\n" + hostOf(u) + "\n\nUserscripts can read and change pages you visit. Only install scripts you trust.")
                        .setPositiveButton("Install") { _, _ -> addScript(code) }
                        .setNegativeButton("Cancel", null).show()
                }
            } catch (e: Exception) { runOnUiThread { toast("Couldn't fetch the script") } }
        }
    }

    private fun showScriptEditor(id: String?) {
        var existing: JSONObject? = null
        if (id != null) {
            val arr = scripts()
            for (i in 0 until arr.length()) if (arr.getJSONObject(i).getString("id") == id) existing = arr.getJSONObject(i)
        }
        val original = existing?.getString("code") ?: TEMPLATE
        var dirty = false
        lateinit var dlg: android.app.Dialog

        fun save(et: EditText) {
            val code = et.text.toString()
            if (code.isBlank()) { toast("Script is empty"); return }
            val a2 = scripts()
            if (id == null) {
                a2.put(JSONObject().put("id", System.currentTimeMillis().toString()).put("name", meta(code).first).put("code", code).put("on", true))
            } else {
                for (i in 0 until a2.length()) {
                    val o = a2.getJSONObject(i)
                    if (o.getString("id") == id) { o.put("code", code); o.put("name", meta(code).first) }
                }
            }
            saveScripts(a2)
            toast("Script saved")
            dirty = false
            dlg.dismiss()
            showUserscripts(keepScroll = true)
        }

        val et = EditText(this).apply {
            setText(original)
            typeface = Typeface.MONOSPACE; textSize = 13f
            setTextColor(col(R.color.text)); setHintTextColor(col(R.color.sub))
            background = null
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(16), dp(12), dp(16), dp(12))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setHorizontallyScrolling(false)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { dirty = true }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        dirty = false

        fun insert(text: String) {
            val st = et.selectionStart.coerceAtLeast(0)
            val en = et.selectionEnd.coerceAtLeast(0)
            et.text.replace(minOf(st, en), maxOf(st, en), text)
        }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(col(R.color.chrome)) }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(12), dp(6))
            setBackgroundColor(col(R.color.bar))
        }
        top.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_close); background = rippleRounded(Color.TRANSPARENT, 24); contentDescription = "Close"
            setOnClickListener { dlg.onBackPressed() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        top.addView(TextView(this).apply {
            text = if (id == null) "New script" else "Edit script"; textSize = 18f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(col(R.color.text)); setPadding(dp(6), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (id != null) top.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_trash); background = rippleRounded(Color.TRANSPARENT, 24); contentDescription = "Delete script"
            setOnClickListener {
                MaterialAlertDialogBuilder(this@MainActivity).setTitle("Delete this script?")
                    .setMessage(meta(et.text.toString()).first)
                    .setPositiveButton("Delete") { _, _ -> updateScripts(id, true); dirty = false; dlg.dismiss(); showUserscripts(keepScroll = true) }
                    .setNegativeButton("Cancel", null).show()
            }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        top.addView(TextView(this).apply {
            text = "Save"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(col(R.color.chrome)); setPadding(dp(20), dp(10), dp(20), dp(10))
            background = rounded(col(R.color.accent), 22)
            setOnClickListener { save(et) }
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(6) })
        root.addView(top)
        root.addView(et, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(10), dp(8), dp(10), dp(8)) }
        fun chip(label: String, ins: String) = tools.addView(TextView(this).apply {
            this.text = label; textSize = 13f; typeface = Typeface.MONOSPACE
            setTextColor(col(R.color.accent)); setPadding(dp(14), dp(8), dp(14), dp(8))
            background = rounded(withAlpha(col(R.color.accent), 36), 18)
            setOnClickListener { insert(ins) }
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(8) })
        chip("// @match", "// @match      https://example.com/*\n")
        chip("// @exclude", "// @exclude    https://example.com/private/*\n")
        chip("GM_addStyle", "GM_addStyle(\"body { }\");\n")
        chip("querySelector", "document.querySelector(\"\")")
        chip("Reset template", "")
        (tools.getChildAt(tools.childCount - 1) as TextView).setOnClickListener { et.setText(TEMPLATE); dirty = true }
        root.addView(View(this).apply { setBackgroundColor(col(R.color.divider)) }, LinearLayout.LayoutParams(MATCH, 1))
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; setBackgroundColor(col(R.color.bar)); addView(tools) })

        dlg = object : android.app.Dialog(this, R.style.Theme_Silu) {
            @Deprecated("Deprecated in Java")
            override fun onBackPressed() {
                if (!dirty) { dismiss(); return }
                MaterialAlertDialogBuilder(this@MainActivity).setTitle("Discard changes?")
                    .setPositiveButton("Discard") { _, _ -> dirty = false; dismiss() }
                    .setNegativeButton("Keep editing", null).show()
            }
        }
        dlg.setContentView(root)
        dlg.window?.apply {
            setLayout(MATCH, MATCH)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setBackgroundDrawable(ColorDrawable(col(R.color.chrome)))
            statusBarColor = col(R.color.bar)
            navigationBarColor = col(R.color.bar)
        }
        dlg.show()
        et.requestFocus()
    }

    private fun installFromLink() {
        val input = EditText(this).apply {
            hint = "https://example.com/script.user.js"; setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val box = FrameLayout(this).apply { setPadding(dp(24), dp(8), dp(24), 0); addView(input) }
        MaterialAlertDialogBuilder(this).setTitle("Install from link").setView(box)
            .setPositiveButton("Fetch") { _, _ ->
                val u = input.text.toString().trim()
                if (u.startsWith("http")) installUserscript(u) else toast("Enter a full link")
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showUserscripts(keepScroll: Boolean = false) {
        showPage("Userscripts", "scripts", keepScroll) { box ->
            val arr = scripts()
            box.addView(row("New script", "Write or paste your own code", R.drawable.ic_plus) { showScriptEditor(null) })
            box.addView(row("Install from link", "Fetch a .user.js file", R.drawable.ic_link) { installFromLink() })
            if (arr.length() == 0) box.addView(note("No userscripts yet.\nTap New script, or open a link ending in .user.js."))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                val on = o.optBoolean("on", true)
                val inc = meta(o.getString("code")).second
                val r = row(o.optString("name"), (if (on) "On" else "Off") + "  •  " + (inc.firstOrNull() ?: "all sites") + "  •  tap to edit", R.drawable.ic_code) {
                    showScriptEditor(id)
                } as LinearLayout
                r.addView(SwitchCompat(this).apply {
                    isChecked = on
                    val st = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
                    thumbTintList = ColorStateList(st, intArrayOf(col(R.color.accent), col(R.color.sub)))
                    trackTintList = ColorStateList(st, intArrayOf(withAlpha(col(R.color.accent), 100), col(R.color.pill)))
                    setOnCheckedChangeListener { _, _ -> updateScripts(id, false); showUserscripts(keepScroll = true) }
                }, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(6); marginEnd = dp(6) })
                box.addView(r)
            }
        }
    }
}