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

