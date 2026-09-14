package pe.com.comparadorprecios.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import pe.com.comparadorprecios.shopping.ShoppingDao
import pe.com.comparadorprecios.shopping.ShoppingItem

@Database(entities = [ScanRecord::class, PriceCheck::class, ShoppingItem::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun shoppingDao(): ShoppingDao

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

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
