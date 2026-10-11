package id.silu.browser
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONArray
import org.json.JSONObject

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class DevTools(private  val a: MainActivity, private  val t: MainActivity.Tab, private  val start: Int = 0) {

    companion object  {
        const val INSPECT_JS = """(function(){
if(window.__si)return;
var si=window.__si={sel:null,box:null,lab:null,picking:false};
function label(el){var s=el.tagName.toLowerCase();if(el.id)s+='#'+el.id;else if(typeof el.className==='string'&&el.className.trim())s+='.'+el.className.trim().split(/\s+/).slice(0,2).join('.');return s}
function selOf(el){
 if(el.id&&document.querySelectorAll('#'+CSS.escape(el.id)).length===1)return '#'+CSS.escape(el.id);
 var p=[];
 for(var n=el;n&&n.nodeType===1&&n!==document.documentElement;n=n.parentElement){
  var s=n.tagName.toLowerCase();
  if(n.id){s+='#'+CSS.escape(n.id);p.unshift(s);break}
  var c=(typeof n.className==='string'?n.className.trim().split(/\s+/).filter(Boolean):[]).slice(0,2);
  if(c.length)s+='.'+c.map(function(x){return CSS.escape(x)}).join('.');
  var sib=n.parentElement?Array.prototype.filter.call(n.parentElement.children,function(k){return k.tagName===n.tagName}):[];
  if(sib.length>1)s+=':nth-of-type('+(sib.indexOf(n)+1)+')';
  p.unshift(s)}
 return p.join(' > ')}
function mk(){if(si.box)return;
 var b=document.createElement('div');b.style.cssText='position:fixed;z-index:2147483647;pointer-events:none;border:2px solid #8ab4f8;background:rgba(138,180,248,.28);box-sizing:border-box;display:none;border-radius:3px';
 var l=document.createElement('div');l.style.cssText='position:fixed;z-index:2147483647;pointer-events:none;background:#8ab4f8;color:#0b1a33;font:600 11px/1 monospace;padding:4px 6px;border-radius:5px;display:none;white-space:nowrap;max-width:90vw;overflow:hidden';
 document.documentElement.appendChild(b);document.documentElement.appendChild(l);si.box=b;si.lab=l}
function hl(el){mk();var r=el.getBoundingClientRect();var b=si.box,l=si.lab;
 b.style.display='block';b.style.left=r.left+'px';b.style.top=r.top+'px';b.style.width=r.width+'px';b.style.height=r.height+'px';
 l.textContent=label(el)+'  '+Math.round(r.width)+'\u00d7'+Math.round(r.height);l.style.display='block';
 l.style.top=(r.top>26?r.top-24:Math.min(r.bottom+4,innerHeight-24))+'px';l.style.left=Math.max(4,Math.min(r.left,innerWidth-140))+'px'}
function usable(el){return el&&el.nodeType===1&&el!==si.box&&el!==si.lab&&el!==document.documentElement}
si.select=function(el){if(!usable(el))return;si.sel=el;hl(el)};
si.go=function(el){if(!usable(el))return;si.sel=el;try{el.scrollIntoView({block:'center',inline:'nearest'})}catch(e){}hl(el)};
si.up=function(){if(si.sel&&si.sel.parentElement)si.go(si.sel.parentElement)};
si.down=function(){if(si.sel&&si.sel.firstElementChild)si.go(si.sel.firstElementChild)};
si.sib=function(d){if(!si.sel)return;si.go(d<0?si.sel.previousElementSibling:si.sel.nextElementSibling)};
si.clear=function(){if(si.box){si.box.style.display='none';si.lab.style.display='none'}};
si.css=function(t){if(si.sel){si.sel.style.cssText=t;hl(si.sel)}};
si.hide=function(){var e=si.sel;if(!e)return;
 if(e.__h!==undefined){e.style.display=e.__h;delete e.__h}else{e.__h=e.style.display;e.style.setProperty('display','none','important')}
 hl(e)};
si.del=function(){var e=si.sel;if(!e)return;var p=e.parentElement;e.remove();si.sel=null;si.clear();if(usable(p))si.go(p)};
si.pick=function(){
 if(si.picking)return;si.picking=true;var last=null,done=false;
 function stop(e){e.preventDefault();e.stopPropagation();if(e.stopImmediatePropagation)e.stopImmediatePropagation()}
 function pt(e){var t=(e.touches&&e.touches[0])||(e.changedTouches&&e.changedTouches[0])||e;return document.elementFromPoint(t.clientX,t.clientY)}
 var h={};
 function finish(el,msg){if(done)return;done=true;si.picking=false;
  Object.keys(h).forEach(function(n){window.removeEventListener(n,h[n],true)});
  if(usable(el))si.select(el);
  try{siluDev.postMessage(usable(el)?'picked':msg)}catch(e){}}
 function mv(e){stop(e);var el=pt(e);if(usable(el)){last=el;hl(el)}}
 function end(e){stop(e);finish(pt(e)||last,'cancel')}
 h.touchstart=mv;h.touchmove=mv;h.touchend=end;h.click=stop;h.mousedown=stop;h.mouseup=stop;h.pointerdown=stop;h.pointerup=stop;
 Object.keys(h).forEach(function(n){window.addEventListener(n,h[n],{capture:true,passive:false})});
 setTimeout(function(){finish(null,'cancel')},30000)};
si.info=function(){
 var el=si.sel;if(!el||!el.isConnected)return null;
 var cs=getComputedStyle(el),r=el.getBoundingClientRect();
 function px(n){return Math.round(parseFloat(cs.getPropertyValue(n))||0)}
 var path=[];for(var n=el;n&&n.nodeType===1;n=n.parentElement)path.unshift(label(n));
 var attrs=[];for(var i=0;i<el.attributes.length;i++){var a=el.attributes[i];attrs.push([a.name,String(a.value).slice(0,400)])}
 var keys=['display','position','width','height','margin','padding','color','background-color','background-image','font-family','font-size','font-weight','line-height','text-align','opacity','z-index','overflow','flex-direction','justify-content','align-items','gap','border','border-radius','box-shadow','transform','cursor','visibility'];
 var st=[];keys.forEach(function(k){var v=cs.getPropertyValue(k);if(v)st.push([k,v.slice(0,200)])});
 return {label:label(el),path:path.join(' \u203a '),selector:selOf(el),
  box:{w:Math.round(r.width),h:Math.round(r.height),m:[px('margin-top'),px('margin-right'),px('margin-bottom'),px('margin-left')],b:[px('border-top-width'),px('border-right-width'),px('border-bottom-width'),px('border-left-width')],p:[px('padding-top'),px('padding-right'),px('padding-bottom'),px('padding-left')]},
  attrs:attrs,styles:st,inline:el.style.cssText||'',html:el.outerHTML.slice(0,8000),text:(el.innerText||'').slice(0,160).replace(/\s+/g,' '),
  kids:el.children.length,hidden:el.__h!==undefined}};
})();"""
    }

    class Entry(val level: String, val msg: String, val src: String = "")

    private lateinit var dialog: BottomSheetDialog
    private lateinit var area: FrameLayout
    private lateinit var tabRow: LinearLayout
    private var page = 0
    private var conFilter = "ALL"
    private var netFilter = "ALL"
    private val names = listOf("Console", "Elements", "Network", "Source", "Storage", "Info")

    private fun dp(v: Int) = a.dp(v)
    private fun col(id: Int) = a.col(id)

    fun show() {
        if (t.web == null) return
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            val r = dp(24).toFloat()
            background = GradientDrawable().apply {
                setColor(col(R.color.chrome))
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        }
        val head = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(8), dp(4))
        }
        val titles = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
        }
        titles.addView(tv("Developer tools", 18f, col(R.color.text), bold = true))
        titles.addView(tv(hostOf(t.url), 12f, col(R.color.sub)))
        head.addView(titles, LinearLayout.LayoutParams(0, WRAP, 1f))
        head.addView(ImageButton(a).apply {
            setImageResource(R.drawable.ic_close)
            background = a.rippleRounded(Color.TRANSPARENT, 22)
            contentDescription = "Close"
            setOnClickListener {
                dialog.dismiss()
            }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(head)

        tabRow = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        root.addView(HorizontalScrollView(a).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabRow)
        })
        area = FrameLayout(a)
        root.addView(area, LinearLayout.LayoutParams(MATCH, 0, 1f))

        dialog = BottomSheetDialog(a)
        dialog.setContentView(root, ViewGroup.LayoutParams(MATCH, (a.resources.displayMetrics.heightPixels * 0.9f).toInt()))
        (root.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.setOnDismissListener {
            t.web?.evaluateJavascript("window.__si&&__si.clear()", null)
        }
        select(start)
        dialog.show()
    }

    private fun select(i: Int) {
        page = i
        tabRow.removeAllViews()
        names.forEachIndexed { idx, n ->
            tabRow.addView(chip(n, idx == i) {
                select(idx)
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply {
                marginEnd = dp(8)
            })
        }
        area.removeAllViews()
        when(i) {
            0 -> console()
            1 -> elements()
            2 -> network()
            3 -> source()
            4 -> storage()
            else -> info()
        }
    }

    private fun tv(text: String, size: Float, color: Int, bold: Boolean = false, mono: Boolean = false): TextView = TextView(a).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        if (mono) typeface = Typeface.MONOSPACE
    }

    private fun chip(label: String, selected: Boolean, onClick: () -> Unit): TextView = TextView(a).apply {
        text = label
        textSize = 13f
        setTextColor(if (selected) col(R.color.accent) else col(R.color.sub))
        typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setPadding(dp(16), dp(8), dp(16), dp(8))
        background = a.rounded(if (selected) withAlpha(col(R.color.accent), 40) else col(R.color.pill), 20)
        setOnClickListener {
            onClick()
        }
    }

    private fun hairline(): View = View(a).apply {
        setBackgroundColor(col(R.color.divider))
    }

    private fun copy(text: String) {
        (a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("SiLu", text))
        Toast.makeText(a, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun note(text: String): TextView = tv(text, 14f, col(R.color.sub)).apply {
        gravity = Gravity.CENTER
        setPadding(dp(32), dp(48), dp(32), 0)
    }

    private fun decode(r: String?): String = try {
        JSONArray("[" + (r ?: "null") + "]").opt(0)?.toString() ?: ""
    } catch (e: Exception) {
        r ?: ""
    }

    private fun js(code: String, cb: (String) -> Unit) {
        val w = t.web
        if (w == null) {
            cb("")
            return
        }
        w.evaluateJavascript(code) { r ->
            if (dialog.isShowing) cb(decode(r))
        }
    }

    private fun size(b: Long): String = when {
        b <= 0 -> "—"
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> String.format("%.1f KB", b / 1024.0)
        else -> String.format("%.2f MB", b / 1048576.0)
    }

    private fun kv(label: String, value: String): View = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(10), dp(4), dp(10))
        addView(tv(label, 11f, col(R.color.sub)))
        addView(tv(value, 13f, col(R.color.text), mono = true).apply {
            setPadding(0, dp(2), 0, 0)
        })
        setOnClickListener {
            copy(value)
        }
    }

    private fun section(title: String): TextView = tv(title.uppercase(), 11f, col(R.color.sub), bold = true).apply {
        letterSpacing = .06f
        setPadding(dp(4), dp(18), dp(4), dp(4))
    }

    private fun actionButton(label: String, accent: Boolean = false, onClick: () -> Unit): TextView = TextView(a).apply {
        text = label
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(if (accent) col(R.color.chrome) else col(R.color.accent))
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = a.rounded(if (accent) col(R.color.accent) else withAlpha(col(R.color.accent), 36), 20)
        setOnClickListener {
            onClick()
        }
    }

    private fun console() {
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
        }
        val filters = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(2), dp(14), dp(6))
        }
        val list = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(8))
        }
        val scroll = ScrollView(a).apply {
            addView(list)
        }

        fun fill() {
            list.removeAllViews()
            val items = t.console.filter {
                when(conFilter) {
                    "ERROR" -> it.level == "ERROR"
                    "WARNING" -> it.level == "WARNING"
                    "LOG" -> it.level != "ERROR" && it.level != "WARNING"
                    else -> true
                }
            }
            if (items.isEmpty()) {
                list.addView(note("No console output yet.\nErrors, warnings and console.log() from this page show up here.\nType JavaScript below to run it on the page."))
                return
            }
            for (e in items) {
                val color = when(e.level) {
                    "ERROR" -> Color.parseColor("#FF7B72")
                    "WARNING" -> Color.parseColor("#F2C14E")
                    "IN" -> col(R.color.accent)
                    "OUT" -> col(R.color.text)
                    else -> col(R.color.text)
                }
                val bg = when(e.level) {
                    "ERROR" -> withAlpha(Color.parseColor("#FF7B72"), 28)
                    "WARNING" -> withAlpha(Color.parseColor("#F2C14E"), 24)
                    else -> Color.TRANSPARENT
                }
                val row = LinearLayout(a).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    background = a.rounded(bg, 10)
                    setOnClickListener {
                        copy(e.msg)
                    }
                }
                val prefix = when(e.level) {
                    "IN" -> "› "
                    "OUT" -> "‹ "
                    else -> ""
                }
                row.addView(tv(prefix + e.msg, 12f, color, mono = true))
                if (e.src.isNotEmpty()) row.addView(tv(e.src, 10f, col(R.color.sub)).apply {
                    setPadding(0, dp(2), 0, 0)
                })
                list.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply {
                    bottomMargin = dp(2)
                })
            }
            scroll.post {
                scroll.fullScroll(View.FOCUS_DOWN)
            }
        }

        fun refreshFilters() {
            filters.removeAllViews()
            listOf("ALL" to "All", "ERROR" to "Errors", "WARNING" to "Warnings", "LOG" to "Logs").forEach { (k, label) ->
                filters.addView(chip(label, conFilter == k) {
                    conFilter = k
                    refreshFilters()
                    fill()
                }, LinearLayout.LayoutParams(WRAP, WRAP).apply {
                    marginEnd = dp(8)
                })
            }
            filters.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
            filters.addView(actionButton("Clear") {
                t.console.clear()
                fill()
            })
        }
        refreshFilters()
        box.addView(filters)
        box.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val input = EditText(a).apply {
            hint = "Run JavaScript…"
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setTextColor(col(R.color.text))
            setHintTextColor(col(R.color.sub))
            background = a.rounded(col(R.color.pill), 22)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_NONE
        }
        fun run() {
            val code = input.text.toString().trim()
            if (code.isEmpty()) return
            input.setText("")
            t.console.add(Entry("IN", code))
            val wrapped = "(function(){try{var r=(0,eval)(" + JSONObject.quote(code) + ");" + "if(r===undefined)return 'undefined';if(typeof r==='function')return String(r);" + "if(typeof r==='object'&&r!==null){try{return JSON.stringify(r,null,2)}catch(e){return String(r)}}return String(r)}" + "catch(e){return 'Error: '+(e&&e.message?e.message:e)}})()"
            t.web?.evaluateJavascript(wrapped) { r ->
                val s = decode(r)
                if (s.contains("unsafe-eval")) {
                    t.web?.evaluateJavascript(code) { r2 ->
                        t.console.add(Entry("OUT", decode(r2)))
                        if (dialog.isShowing) fill()
                    }
                } else {
                    t.console.add(Entry(if (s.startsWith("Error: ")) "ERROR" else "OUT", s))
                    if (dialog.isShowing) fill()
                }
            }
            fill()
        }
        val send = actionButton("Run", accent = true) {
            run()
        }
        val bar = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
            addView(input, LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                marginEnd = dp(8)
            })
            addView(send)
        }
        box.addView(hairline(), LinearLayout.LayoutParams(MATCH, 1))
        box.addView(bar)
        area.addView(box, FrameLayout.LayoutParams(MATCH, MATCH))
        fill()
    }

    private fun pick() {
        dialog.dismiss()
        t.web?.evaluateJavascript(INSPECT_JS, null)
        t.web?.evaluateJavascript("__si.pick()", null)
        Toast.makeText(a, "Tap an element on the page", Toast.LENGTH_LONG).show()
    }

    private fun elements() {
        val list = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(6), dp(14), dp(28))
        }
        area.addView(ScrollView(a).apply {
            addView(list)
        }, FrameLayout.LayoutParams(MATCH, MATCH))
        t.web?.evaluateJavascript(INSPECT_JS, null)
        loadElement(list)
    }

    private fun loadElement(list: LinearLayout) {
        js("(function(){return window.__si&&__si.sel?JSON.stringify(__si.info()):''})()") { r ->
            list.removeAllViews()
            val o = try {
                if (r.isEmpty() || r == "null") null else JSONObject(r)
            } catch (e: Exception) {
                null
            }
            if (o == null) emptyElement(list) else renderElement(list, o)
        }
    }

    private fun emptyElement(list: LinearLayout) {
        list.addView(note("Inspect any element on the page.\nTap Pick, then tap what you want to look at. Layout, styles and HTML show up here, and you can edit, hide or delete it."))
        list.addView(actionButton("Pick element", accent = true) {
            pick()
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(24)
        })
    }

    private fun act(list: LinearLayout, code: String) {
        t.web?.evaluateJavascript(code) {
            if (dialog.isShowing) loadElement(list)
        }
    }

    private fun ints(arr: JSONArray?): IntArray = IntArray(4) {
        arr?.optInt(it) ?: 0
    }

    private fun num(n: Int): TextView = tv(n.toString(), 11f, col(R.color.sub), mono = true).apply {
        gravity = Gravity.CENTER
        minWidth = dp(18)
    }

    private fun layer(color: Int, v: IntArray, inner: View): View {
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = a.rounded(withAlpha(color, 44), 12, withAlpha(color, 130), 1)
        }
        box.addView(num(v[0]))
        val mid = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        mid.addView(num(v[3]))
        mid.addView(inner, LinearLayout.LayoutParams(0, WRAP, 1f).apply {
            marginStart = dp(4)
            marginEnd = dp(4)
        })
        mid.addView(num(v[1]))
        box.addView(mid, LinearLayout.LayoutParams(MATCH, WRAP))
        box.addView(num(v[2]))
        return box
    }

    private fun renderElement(list: LinearLayout, o: JSONObject) {
        list.addView(tv(o.optString("label"), 17f, col(R.color.accent), bold = true, mono = true).apply {
            setPadding(dp(4), dp(4), dp(4), 0)
        })
        list.addView(HorizontalScrollView(a).apply {
            isHorizontalScrollBarEnabled = false
            addView(tv(o.optString("path"), 11f, col(R.color.sub), mono = true).apply {
                setPadding(dp(4), dp(4), dp(4), dp(6))
            })
        })
        val text = o.optString("text")
        if (text.isNotBlank()) list.addView(tv("“$text”", 12f, col(R.color.sub)).apply {
            setPadding(dp(4), 0, dp(4), dp(6))
        })

        val nav = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(2), dp(4), dp(2), dp(4))
        }
        fun navBtn(label: String, code: String, accent: Boolean = false) = nav.addView(actionButton(label, accent) {
            act(list, code)
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply {
            marginEnd = dp(8)
        })
        nav.addView(actionButton("Pick", accent = true) {
            pick()
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply {
            marginEnd = dp(8)
        })
        navBtn("↑ Parent", "__si.up()")
        if (o.optInt("kids") > 0) navBtn("↓ Child", "__si.down()")
        navBtn("← Prev", "__si.sib(-1)")
        navBtn("Next →", "__si.sib(1)")
        list.addView(HorizontalScrollView(a).apply {
            isHorizontalScrollBarEnabled = false
            addView(nav)
        })

        list.addView(section("Box model"))
        val bx = o.optJSONObject("box")
        if (bx != null) {
            val content = tv("${bx.optInt("w")} × ${bx.optInt("h")}", 13f, col(R.color.text), mono = true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(12), dp(8), dp(12))
                background = a.rounded(withAlpha(Color.parseColor("#8AB4F8"), 80), 8)
            }
            var inner: View = content
            inner = layer(Color.parseColor("#7BC47F"), ints(bx.optJSONArray("p")), inner)
            inner = layer(Color.parseColor("#F2C14E"), ints(bx.optJSONArray("b")), inner)
            inner = layer(Color.parseColor("#F2994A"), ints(bx.optJSONArray("m")), inner)
            list.addView(inner, LinearLayout.LayoutParams(MATCH, WRAP).apply {
                topMargin = dp(4)
            })
            list.addView(tv("margin  •  border  •  padding  •  content", 10f, col(R.color.sub)).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
        }

        list.addView(section("Actions"))
        val acts = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(2), dp(4), dp(2), dp(4))
        }
        fun actBtn(label: String, onClick: () -> Unit) = acts.addView(actionButton(label) {
            onClick()
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply {
            marginEnd = dp(8)
        })
        actBtn("Copy selector") {
            copy(o.optString("selector"))
        }
        actBtn("Copy HTML") {
            copy(o.optString("html"))
        }
        actBtn("Edit style") {
            editStyle(list, o.optString("inline"))
        }
        actBtn(if (o.optBoolean("hidden")) "Show" else "Hide") {
            act(list, "__si.hide()")
        }
        actBtn("Delete") {
            act(list, "__si.del()")
        }
        list.addView(HorizontalScrollView(a).apply {
            isHorizontalScrollBarEnabled = false
            addView(acts)
        })
        list.addView(kv("Selector", o.optString("selector")))

        val attrs = o.optJSONArray("attrs")
        list.addView(section("Attributes (${attrs?.length() ?: 0})"))
        if (attrs == null || attrs.length() == 0) list.addView(tv("None", 13f, col(R.color.sub)).apply {
            setPadding(dp(4), dp(6), 0, 0)
        }) else for (i in 0 until attrs.length()) {
            val p = attrs.getJSONArray(i)
            list.addView(kv(p.getString(0), p.optString(1)))
        }

        val st = o.optJSONArray("styles")
        list.addView(section("Computed styles"))
        if (st != null) for (i in 0 until st.length()) {
            val p = st.getJSONArray(i)
            list.addView(kv(p.getString(0), p.optString(1)))
        }

        list.addView(section("HTML"))
        val html = tv(o.optString("html"), 11f, col(R.color.text), mono = true).apply {
            setTextIsSelectable(true)
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        list.addView(HorizontalScrollView(a).apply {
            addView(html)
        })
    }

    private fun editStyle(list: LinearLayout, current: String) {
        val et = EditText(a).apply {
            setText(current)
            hint = "color: red; font-size: 20px;"
            typeface = Typeface.MONOSPACE
            textSize = 13f
            minLines = 5
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val box = FrameLayout(a).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(et)
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(a).setTitle("Inline style").setView(box).setPositiveButton("Apply") { _, _ ->
            act(list, "__si.css(" + JSONObject.quote(et.text.toString()) + ")")
        }.setNegativeButton("Cancel", null).show()
    }

    private class Req(val url: String, val type: String, val ms: Int, val bytes: Long, val status: Int) {
        val kind: String
            get() {
                val ext = url.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase()
                return when {
                    type == "document" -> "doc"
                    type == "xmlhttprequest" || type == "fetch" || type == "beacon" -> "xhr"
                    type == "script" || ext == "js" || ext == "mjs" -> "js"
                    type == "css" || ext == "css" -> "css"
                    type == "img" || type == "image" || ext in setOf("png", "jpg", "jpeg", "gif", "webp", "avif", "svg", "ico") -> "img"
                    else -> "other"
                }
            }

    }

    private fun network() {
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
        }
        val summary = tv("Loading…", 12f, col(R.color.sub)).apply {
            setPadding(dp(18), dp(6), dp(18), dp(4))
        }
        val filters = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(2), dp(14), dp(6))
        }
        val list = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(2), dp(14), dp(14))
        }
        var all: List<Req> = emptyList()

        fun fill() {
            list.removeAllViews()
            val items = all.filter {
                netFilter == "ALL" || it.kind == netFilter
            }
            if (items.isEmpty()) {
                list.addView(note("No requests to show."))
                return
            }
            for (r in items.take(300)) {
                val row = LinearLayout(a).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                    background = a.rippleRounded(Color.TRANSPARENT, 12)
                    setOnClickListener {
                        copy(r.url)
                    }
                }
                val u = Uri.parse(r.url)
                val name = (u.lastPathSegment ?: u.host ?: r.url).ifEmpty {
                    u.host ?: r.url
                }
                row.addView(tv(name, 13f, col(R.color.text), mono = true).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.MIDDLE
                })
                val st = if (r.status > 0) "${r.status}  •  " else ""
                row.addView(tv("$st${r.type.ifEmpty {
                    r.kind
                }}  •  ${size(r.bytes)}  •  ${r.ms} ms  •  ${u.host ?: ""}", 11f, if (r.status >= 400) Color.parseColor("#FF7B72") else col(R.color.sub)).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(2), 0, 0)
                })
                list.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
            }
            if (items.size > 300) list.addView(note("Showing the first 300 of ${items.size} requests."))
        }

        fun refreshFilters() {
            filters.removeAllViews()
            listOf("ALL" to "All", "doc" to "Doc", "js" to "JS", "css" to "CSS", "img" to "Img", "xhr" to "XHR", "other" to "Other").forEach { (k, label) ->
                filters.addView(chip(label, netFilter == k) {
                    netFilter = k
                    refreshFilters()
                    fill()
                }, LinearLayout.LayoutParams(WRAP, WRAP).apply {
                    marginEnd = dp(8)
                })
            }
        }

        fun load() {
            summary.text = "Loading…"
            js("(function(){var o=[];var n=performance.getEntriesByType('navigation')[0];" + "if(n)o.push({n:location.href,t:'document',d:Math.round(n.duration),s:n.transferSize||0,st:n.responseStatus||0});" + "performance.getEntriesByType('resource').forEach(function(e){o.push({n:e.name,t:e.initiatorType||'',d:Math.round(e.duration),s:e.transferSize||e.encodedBodySize||0,st:e.responseStatus||0})});" + "return JSON.stringify(o)})()") { r ->
                val out = ArrayList<Req>()
                try {
                    val arr = JSONArray(r)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        out.add(Req(o.getString("n"), o.optString("t"), o.optInt("d"), o.optLong("s"), o.optInt("st")))
                    }
                } catch (e: Exception) {}
                all = out
                val total = out.sumOf {
                    it.bytes
                }
                summary.text = "${out.size} requests  •  ${size(total)} transferred"
                fill()
            }
        }

        val head = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(summary, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(actionButton("Refresh") {
                load()
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply {
                marginEnd = dp(14)
            })
        }
        refreshFilters()
        box.addView(head)
        box.addView(HorizontalScrollView(a).apply {
            isHorizontalScrollBarEnabled = false
            addView(filters)
        })
        box.addView(ScrollView(a).apply {
            addView(list)
        }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        area.addView(box, FrameLayout.LayoutParams(MATCH, MATCH))
        load()
    }

    private fun source() {
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
        }
        val summary = tv("Loading…", 12f, col(R.color.sub)).apply {
            setPadding(dp(18), dp(6), dp(18), dp(4))
        }
        val code = tv("", 11f, col(R.color.text), mono = true).apply {
            setTextIsSelectable(true)
            setPadding(dp(14), dp(8), dp(14), dp(14))
        }
        var current = ""
        fun load() {
            summary.text = "Loading…"
            js("(function(){var h='<!DOCTYPE html>\\n'+document.documentElement.outerHTML;return h.length>200000?h.slice(0,200000)+'\\n… (truncated)':h})()") { s ->
                current = s
                code.text = s
                summary.text = "Rendered DOM  •  ${s.count {
                    it == '\n'
                } + 1} lines  •  ${size(s.length.toLong())}"
            }
        }
        val head = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(summary, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(actionButton("Copy") {
                if (current.isNotEmpty()) copy(current)
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply {
                marginEnd = dp(8)
            })
            addView(actionButton("Refresh") {
                load()
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply {
                marginEnd = dp(14)
            })
        }
        box.addView(head)
        val h = HorizontalScrollView(a).apply {
            addView(code)
        }
        box.addView(ScrollView(a).apply {
            addView(h)
        }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        area.addView(box, FrameLayout.LayoutParams(MATCH, MATCH))
        load()
    }

    private fun storage() {
        val list = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(2), dp(14), dp(20))
        }
        val sv = ScrollView(a).apply {
            addView(list)
        }

        fun entryRow(k: String, v: String, onDelete: () -> Unit): View = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(0), dp(6))
            val c = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
            }
            c.addView(tv(k, 13f, col(R.color.accent), mono = true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            c.addView(tv(if (v.length > 240) v.take(240) + "…" else v, 12f, col(R.color.text), mono = true).apply {
                setPadding(0, dp(2), 0, 0)
            })
            addView(c, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(ImageButton(a).apply {
                setImageResource(R.drawable.ic_close)
                background = a.rippleRounded(Color.TRANSPARENT, 18)
                contentDescription = "Delete"
                setOnClickListener {
                    onDelete()
                }
            }, LinearLayout.LayoutParams(dp(40), dp(40)))
            setOnClickListener {
                copy("$k=$v")
            }
        }

        fun cookies(cb: (List<Pair<String, String>>) -> Unit) {
            fun parse(s: String?): List<Pair<String, String>> = (s ?: "").split(";").map {
                it.trim()
            }.filter {
                it.isNotEmpty()
            }.map {
                it.substringBefore('=') to it.substringAfter('=', "")
            }
            if (t.secret) js("document.cookie") {
                cb(parse(it))
            } else cb(parse(CookieManager.getInstance().getCookie(t.url)))
        }

        fun load() {
            list.removeAllViews()
            cookies { ck ->
                js("(function(){try{return JSON.stringify({l:Object.assign({},localStorage),s:Object.assign({},sessionStorage)})}catch(e){return '{}'}})()") { r ->
                    list.removeAllViews()
                    val o = try {
                        JSONObject(r)
                    } catch (e: Exception) {
                        JSONObject()
                    }
                    list.addView(section("Cookies (${ck.size})"))
                    if (ck.isEmpty()) list.addView(tv("None", 13f, col(R.color.sub)).apply {
                        setPadding(dp(4), dp(6), 0, 0)
                    })
                    for ((k, v) in ck) list.addView(entryRow(k, v) {
                        CookieManager.getInstance().setCookie(t.url, "$k=; Max-Age=0; Path=/")
                        CookieManager.getInstance().flush()
                        t.web?.evaluateJavascript("document.cookie='${k.replace("'", "")}=; Max-Age=0; path=/'", null)
                        load()
                    })
                    for ((key, label) in listOf("l" to "localStorage", "s" to "sessionStorage")) {
                        val m = o.optJSONObject(key) ?: JSONObject()
                        list.addView(section("$label (${m.length()})"))
                        if (m.length() == 0) list.addView(tv("Empty", 13f, col(R.color.sub)).apply {
                            setPadding(dp(4), dp(6), 0, 0)
                        })
                        val keys = m.keys().asSequence().toList().sorted()
                        for (k in keys) list.addView(entryRow(k, m.optString(k)) {
                            t.web?.evaluateJavascript("$label.removeItem(" + JSONObject.quote(k) + ")", null)
                            load()
                        })
                    }
                    if (t.secret) list.addView(tv("Secret tab: only cookies visible to scripts are shown.", 11f, col(R.color.sub)).apply {
                        setPadding(dp(4), dp(14), 0, 0)
                    })
                }
            }
        }
        area.addView(sv, FrameLayout.LayoutParams(MATCH, MATCH))
        load()
    }

    private fun info() {
        val list = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(2), dp(14), dp(24))
        }
        area.addView(ScrollView(a).apply {
            addView(list)
        }, FrameLayout.LayoutParams(MATCH, MATCH))

        list.addView(section("Page"))
        list.addView(kv("URL", t.url))
        list.addView(kv("Title", t.title.ifBlank {
            "—"
        }))
        list.addView(kv("Desktop site", if (t.desktop) "On" else "Off"))
        list.addView(kv("Blocked on this page", t.blocked.get().toString()))
        val holder = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
        }
        list.addView(holder)

        js("(function(){var n=performance.getEntriesByType('navigation')[0];var tm=n?{dns:Math.round(n.domainLookupEnd-n.domainLookupStart),tcp:Math.round(n.connectEnd-n.connectStart),ttfb:Math.round(n.responseStart-n.requestStart),dcl:Math.round(n.domContentLoadedEventEnd),load:Math.round(n.loadEventEnd)}:null;" + "return JSON.stringify({ua:navigator.userAgent,vw:innerWidth,vh:innerHeight,dpr:devicePixelRatio,rs:document.readyState,cs:document.characterSet,sec:isSecureContext,nodes:document.getElementsByTagName('*').length,lang:navigator.language,mem:(performance.memory?Math.round(performance.memory.usedJSHeapSize/1048576):null),tm:tm})})()") { r ->
            val o = try {
                JSONObject(r)
            } catch (e: Exception) {
                JSONObject()
            }
            holder.removeAllViews()
            holder.addView(kv("Document", "${o.optString("rs")}  •  ${o.optString("cs")}  •  ${o.optInt("nodes")} nodes  •  " + if (o.optBoolean("sec")) "secure context" else "insecure context"))
            holder.addView(kv("Viewport", "${o.optInt("vw")} × ${o.optInt("vh")}  @ ${o.optDouble("dpr")}x  •  ${o.optString("lang")}"))
            if (!o.isNull("mem")) holder.addView(kv("JS heap", "${o.optInt("mem")} MB"))
            val tm = o.optJSONObject("tm")
            if (tm != null) holder.addView(kv("Timing", "DNS ${tm.optInt("dns")} ms  •  connect ${tm.optInt("tcp")} ms  •  first byte ${tm.optInt("ttfb")} ms\nDOMContentLoaded ${tm.optInt("dcl")} ms  •  load ${tm.optInt("load")} ms"))
            holder.addView(kv("User agent", o.optString("ua")))
        }

        val wv = try {
            WebView.getCurrentWebViewPackage()?.let {
                it.packageName + " " + it.versionName
            }
        } catch (e: Exception) {
            null
        }
        if (wv != null) list.addView(kv("WebView", wv))

        list.addView(section("Actions"))
        val actions = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        actions.addView(actionButton("Hard reload", accent = true) {
            t.web?.clearCache(true)
            t.web?.reload()
            dialog.dismiss()
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply {
            marginEnd = dp(8)
        })
        actions.addView(actionButton("Copy URL") {
            copy(t.url)
        })
        list.addView(actions)

        val usb = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(14), dp(4), dp(4))
        }
        val tx = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
        }
        tx.addView(tv("USB debugging", 14f, col(R.color.text)))
        tx.addView(tv("Inspect tabs from a computer with chrome://inspect", 12f, col(R.color.sub)))
        usb.addView(tx, LinearLayout.LayoutParams(0, WRAP, 1f))
        val on = a.prefs().getBoolean("usbdebug", false)
        usb.addView(SwitchCompat(a).apply {
            isChecked = on
            setOnCheckedChangeListener { _, v ->
                a.prefs().edit().putBoolean("usbdebug", v).apply()
                WebView.setWebContentsDebuggingEnabled(v)
                Toast.makeText(a, if (v) "USB debugging on" else "USB debugging off", Toast.LENGTH_SHORT).show()
            }
        })
        list.addView(usb)
    }
}

