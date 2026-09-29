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
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
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

        // v4: confirmar/corregir queda disponible y no rompe lo que ya había.
        assertTrue(history.findConfirmedMatch(scans[0].identification) == null)
        history.confirm(scans[0].id)
        assertTrue(history.findConfirmedMatch(scans[0].identification) != null)
    }

    @Test
    fun `migrar de v3 a v4 conserva los escaneos y agrega confirmacion sin romper nada`() = runTest {
        // Esquema exacto que generaba Room para la versión 3 (antes de confirmed/correctionNote).
        context.deleteDatabase(dbName)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null).use { v3 ->
            v3.execSQL(
                "CREATE TABLE IF NOT EXISTS scans (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "createdAt INTEGER NOT NULL, marca TEXT NOT NULL, nombre TEXT NOT NULL, presentacion TEXT NOT NULL, " +
                    "categoria TEXT NOT NULL, confianza REAL NOT NULL, tiendasJson TEXT NOT NULL, bestPrice REAL, " +
                    "thumbnail BLOB NOT NULL, tipo TEXT NOT NULL DEFAULT '')"
            )
            v3.execSQL(
                "INSERT INTO scans (createdAt, marca, nombre, presentacion, categoria, tipo, confianza, tiendasJson, bestPrice, thumbnail) " +
                    "VALUES (1000, 'AJE', 'Free Tea Frutos Rojos', '500ml', 'bebidas', 'te helado', 0.9, '[]', 3.5, x'01')"
            )
            v3.execSQL(
                "CREATE TABLE IF NOT EXISTS price_checks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, scanId INTEGER NOT NULL, " +
                    "checkedAt INTEGER NOT NULL, tiendasJson TEXT NOT NULL, bestPrice REAL)"
            )
            v3.execSQL(
                "CREATE TABLE IF NOT EXISTS shopping_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, addedAt INTEGER NOT NULL, " +
                    "marca TEXT NOT NULL, nombre TEXT NOT NULL, presentacion TEXT NOT NULL, categoria TEXT NOT NULL, tipo TEXT NOT NULL, " +
                    "quantity INTEGER NOT NULL, tiendasJson TEXT NOT NULL, pricesUpdatedAt INTEGER NOT NULL)"
            )
            v3.execSQL(
                "CREATE TABLE IF NOT EXISTS pending_scans (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, createdAt INTEGER NOT NULL, " +
                    "imagePath TEXT NOT NULL, thumbnail BLOB, categoria TEXT, lastError TEXT)"
            )
            v3.version = 3
        }

        db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        val history = HistoryRepository(db!!.historyDao())

        val scans = history.all.first()
        assertEquals(1, scans.size)
        val identification = scans[0].identification

        // Sin confirmar todavía: no hay coincidencia previa.
        assertTrue(history.findConfirmedMatch(identification) == null)

        history.confirm(scans[0].id)
        history.saveCorrection(scans[0].id, "nota de prueba, no debería afectar la confirmación")

        val confirmedMatch = history.findConfirmedMatch(identification)
        assertTrue(confirmedMatch != null)
        assertEquals(scans[0].id, confirmedMatch!!.id)
    }
}
