package pe.com.comparadorprecios.history

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.pending.PendingScanRepository
import pe.com.comparadorprecios.shopping.ShoppingListRepository
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppDatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val dbName = "migration-test.db"
    private var db: AppDatabase? = null

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `migrar de v1 a v2 conserva los escaneos y habilita lista y revisiones`() = runTest {
        // Esquema exacto que generaba Room para la versión 1 (solo la tabla scans).
        context.deleteDatabase(dbName)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null).use { v1 ->
            v1.execSQL(
                "CREATE TABLE IF NOT EXISTS scans (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "createdAt INTEGER NOT NULL, marca TEXT NOT NULL, nombre TEXT NOT NULL, presentacion TEXT NOT NULL, " +
                    "categoria TEXT NOT NULL, confianza REAL NOT NULL, tiendasJson TEXT NOT NULL, bestPrice REAL, " +
                    "thumbnail BLOB NOT NULL)"
            )
            v1.execSQL(
                "INSERT INTO scans (createdAt, marca, nombre, presentacion, categoria, confianza, tiendasJson, bestPrice, thumbnail) " +
                    "VALUES (1000, 'Gloria', 'Leche evaporada', '400g', 'abarrotes', 0.9, '[]', 4.5, x'01')"
            )
            v1.version = 1
        }

        db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        val history = HistoryRepository(db!!.historyDao())

        val scans = history.all.first()
        assertEquals(1, scans.size)
        assertEquals("", scans[0].identification.tipo)

        history.saveCheck(scans[0].id, emptyList())
        ShoppingListRepository(db!!.shoppingDao()).add(Identification("Gloria", "Leche", "400g", "abarrotes", 0.9), emptyList())
        assertTrue(db!!.shoppingDao().getAll().isNotEmpty())

        // v3: la cola sin conexión queda disponible sin perder lo anterior.
        PendingScanRepository(db!!.pendingScanDao(), File(context.cacheDir, "pending-migration-test"))
            .enqueue("data:image/jpeg;base64,ABC", null, categoria = null)
        assertTrue(db!!.pendingScanDao().getAll().isNotEmpty())
        assertEquals(1, history.all.first().size)
    }
}
