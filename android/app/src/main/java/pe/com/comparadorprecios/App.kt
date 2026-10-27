package pe.com.comparadorprecios

import android.app.Application
import com.clerk.api.Clerk
import org.osmdroid.config.Configuration
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Clerk.initialize(this, publishableKey = BuildConfig.CLERK_PUBLISHABLE_KEY)
        // La política de tiles de OpenStreetMap bloquea clientes sin User-Agent propio; la caché va
        // en la carpeta privada de la app (sin permiso de almacenamiento externo).
        Configuration.getInstance().apply {
            userAgentValue = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}"
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
    }
}
