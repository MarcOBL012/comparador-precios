package pe.com.comparadorprecios

import android.app.Application
import com.clerk.api.Clerk

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Clerk.initialize(this, publishableKey = BuildConfig.CLERK_PUBLISHABLE_KEY)
    }
}
