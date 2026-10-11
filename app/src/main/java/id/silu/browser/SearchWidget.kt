package id.silu.browser
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class SearchWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_search)
            views.setOnClickPendingIntent(R.id.widget_search, launch(context, MainActivity.ACTION_SEARCH, 1))
            views.setOnClickPendingIntent(R.id.widget_secret, launch(context, MainActivity.ACTION_SEARCH_SECRET, 2))
            manager.updateAppWidget(id, views)
        }
    }

    private fun launch(c: Context, action: String, code: Int): PendingIntent = PendingIntent.getActivity(c, code, Intent(c, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

