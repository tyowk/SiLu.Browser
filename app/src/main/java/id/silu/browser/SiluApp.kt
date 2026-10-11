package id.silu.browser
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.appcompat.app.AppCompatDelegate

class SiluApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppSettings(this).nightMode())
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CH_WEB, "Website notifications", NotificationManager.IMPORTANCE_DEFAULT))
    }

    companion object  {
        const val CH_WEB = "web_notifications"
    }
}


