package id.silu.browser
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.text.InputType
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun Context.col(id: Int): Int = ContextCompat.getColor(this, id)

object Motion {
    val enter = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    val exit = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
    val soft = PathInterpolator(0.2f, 0f, 0f, 1f)
}

fun Context.rounded(c: Int, r: Int, stroke: Int? = null, strokeDp: Int = 2): GradientDrawable = GradientDrawable().apply {
    setColor(c)
    cornerRadius = dp(r).toFloat()
    if (stroke != null) setStroke(dp(strokeDp), stroke)
}

fun Context.rippleRounded(bg: Int, r: Int): RippleDrawable = RippleDrawable(ColorStateList.valueOf(col(R.color.divider)), rounded(bg, r), rounded(Color.WHITE, r))

fun Context.selectable(): Drawable? {
    val tv = TypedValue()
    theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
    return ContextCompat.getDrawable(this, tv.resourceId)
}

fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

fun hostOf(u: String): String = try {
    Uri.parse(u).host?.removePrefix("www.") ?: u
} catch (e: Exception) {
    u
}

fun View.pressable() {
    setOnTouchListener { v, e ->
        when(e.action) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(.95f).scaleY(.95f).setDuration(110).setInterpolator(Motion.enter).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(1.8f)).start()
        }
        false
    }
}

fun View.swipeDismiss(onDismiss: () -> Unit) {
    val slop = ViewConfiguration.get(context).scaledTouchSlop
    var downX = 0f
    var downY = 0f
    var dragging = false
    setOnTouchListener { v, e ->
        var consumed = false
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                dragging = false
                v.animate().scaleX(.97f).scaleY(.97f).setDuration(110).setInterpolator(Motion.enter).start()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downX
                val dy = e.rawY - downY
                if (!dragging && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    dragging = true
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    v.isPressed = false
                    v.cancelLongPress()
                }
                if (dragging) {
                    v.translationX = dx
                    v.rotation = dx / v.width.coerceAtLeast(1) * 6f
                    v.alpha = (1f - Math.abs(dx) / (v.width.coerceAtLeast(1) * 1.1f)).coerceIn(.25f, 1f)
                    consumed = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    consumed = true
                    val dx = v.translationX
                    if (e.actionMasked == MotionEvent.ACTION_UP && Math.abs(dx) > v.width * .35f) {
                        val dir = if (dx > 0) 1 else -1
                        v.animate().translationX(dir * v.width * 1.3f).alpha(0f).setDuration(200).setInterpolator(Motion.exit).withEndAction {
                            onDismiss()
                        }.start()
                    } else {
                        v.animate().translationX(0f).rotation(0f).alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(Motion.enter).start()
                    }
                } else {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(1.8f)).start()
                }
                dragging = false
            }
        }
        consumed
    }
}

fun Context.shortcutDialog(old: Entry?, onSave: (String, String) -> Unit) {
    val name = EditText(this).apply {
        hint = "Name"
        setText(old?.title ?: "")
        setSingleLine()
    }
    val url = EditText(this).apply {
        hint = "https://example.com"
        setText(old?.url ?: "")
        setSingleLine()
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
    }
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(8), dp(24), 0)
        addView(name)
        addView(url)
    }
    MaterialAlertDialogBuilder(this).setTitle(if (old == null) "Add shortcut" else "Edit shortcut").setView(box).setPositiveButton("Save") { _, _ ->
        var u = url.text.toString().trim()
        if (u.isNotEmpty() && !u.contains("://")) u = "https://$u"
        if (u.isNotEmpty()) onSave(name.text.toString().trim().ifEmpty {
            hostOf(u)
        }, u)
    }.setNegativeButton("Cancel", null).show()
}

class SegPillDrawable(private  val base: Drawable?, private  val pad: Float) : Drawable() {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val rect = android.graphics.RectF()
    var pos = 0f
        set(v) {
            field = v
            invalidateSelf()
        }

    var color = 0
        set(v) {
            field = v
            paint.color = v
            invalidateSelf()
        }


    override fun draw(canvas: android.graphics.Canvas) {
        val b = bounds
        base?.let {
            it.setBounds(b.left, b.top, b.right, b.bottom)
            it.draw(canvas)
        }
        val segW = (b.width() - 2 * pad) / 2f
        val h = b.height() - 2 * pad
        val left = b.left + pad + pos * segW
        rect.set(left, b.top + pad, left + segW, b.top + pad + h)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
    }
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}

