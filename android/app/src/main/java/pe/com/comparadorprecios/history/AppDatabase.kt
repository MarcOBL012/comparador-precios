package pe.com.comparadorprecios.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import pe.com.comparadorprecios.pending.PendingScan
import pe.com.comparadorprecios.pending.PendingScanDao
import pe.com.comparadorprecios.shopping.ShoppingDao
import pe.com.comparadorprecios.shopping.ShoppingItem

@Database(
    entities = [ScanRecord::class, PriceCheck::class, ShoppingItem::class, PendingScan::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun pendingScanDao(): PendingScanDao

    companion object {
        const val NAME = "comparador-precios.db"

        /** Conserva el historial de quienes ya tenían la app instalada. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scans ADD COLUMN tipo TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS price_checks (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, scanId INTEGER NOT NULL, " +
                        "checkedAt INTEGER NOT NULL, tiendasJson TEXT NOT NULL, bestPrice REAL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_price_checks_scanId ON price_checks (scanId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS shopping_items (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, addedAt INTEGER NOT NULL, " +
                        "marca TEXT NOT NULL, nombre TEXT NOT NULL, presentacion TEXT NOT NULL, " +
                        "categoria TEXT NOT NULL, tipo TEXT NOT NULL, quantity INTEGER NOT NULL, " +
                        "tiendasJson TEXT NOT NULL, pricesUpdatedAt INTEGER NOT NULL)"
                )
            }
        }

        /** Cola de fotos tomadas sin conexión (modo SIN CONEXIÓN). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pending_scans (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, createdAt INTEGER NOT NULL, " +
                        "imagePath TEXT NOT NULL, thumbnail BLOB, categoria TEXT, lastError TEXT)"
                )
            }
        }

        /** Confirmación del producto ("¿es este?") y corrección manual cuando no lo es. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scans ADD COLUMN confirmed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scans ADD COLUMN correctionNote TEXT")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
