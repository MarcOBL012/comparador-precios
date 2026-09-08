# Rediseño M3 Expressive + Historial local Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rediseñar las pantallas con Material 3 Expressive y agregar historial local de escaneos (Room) con catálogo, detalle y borrado, navegados por barra inferior.

**Architecture:** Navigation Compose con 3 destinos (`scan`, `history`, `detail/{scanId}`); nueva capa `history/` (entidad + DAO + repositorio + ViewModels) aislada del flujo de escaneo, que sigue gobernado por `ScanUiState` dentro del destino `scan` y persiste cada `Success` en segundo plano.

**Tech Stack:** Kotlin 2.0.20, AGP 8.5.2, Compose BOM 2024.10.00, Navigation Compose 2.7.7 (ya declarado), Room 2.6.1 (runtime + ktx + compiler vía KSP 2.0.20-1.0.24), Robolectric 4.13 + room-testing 2.6.1 para tests de DAO en JVM, JUnit 4 + coroutines-test (existentes).

**Spec:** `docs/superpowers/specs/2026-09-08-historial-rediseno-design.md`

## Global Constraints

- Material 3 Expressive con componentes estándar; sin marca custom.
- Base solo en el dispositivo; sin cuentas ni red para el historial.
- Miniatura JPEG ~256px por el lado largo; si excede 200_000 bytes se recomprime (calidades 85 → 70 → 55 → 40). Nunca se guarda la foto completa.
- Catálogo ordenado por recientes primero (createdAt DESC).
- Borrado con confirmación + Snackbar con Deshacer.
- Los escaneos de baja confianza (`tiendas` vacía) no se guardan.
- El guardado nunca bloquea el resultado: `Success` se muestra aunque Room falle.
- Se muestra el nombre completo del producto junto al precio (mitigación del bug de multipacks, heredada del Plan 3).
- Cero tiendas hardcodeadas en UI (compatible con futuro Plan 2b).
- minSdk 26: el color dinámico solo aplica en Android 12+ (guardia de versión con fallback estático).

---

## File Structure

- `android/build.gradle.kts` (modify: agrega plugin KSP 2.0.20-1.0.24).
- `android/app/build.gradle.kts` (modify: dependencias Room + Robolectric).
- `android/app/src/main/java/pe/com/comparadorprecios/history/ScanRecord.kt` (nueva: entidad Room).
- `android/app/src/main/java/pe/com/comparadorprecios/history/HistoryDao.kt` (nuevo: DAO).
- `android/app/src/main/java/pe/com/comparadorprecios/history/AppDatabase.kt` (nueva: RoomDatabase).
- `android/app/src/main/java/pe/com/comparadorprecios/history/HistoryItem.kt` (nuevo: modelo de UI).
- `android/app/src/main/java/pe/com/comparadorprecios/history/HistoryRepository.kt` (nuevo: mapeo + operaciones).
- `android/app/src/main/java/pe/com/comparadorprecios/util/ThumbnailSpec.kt` (nuevo: matemática pura JVM).
- `android/app/src/main/java/pe/com/comparadorprecios/util/ThumbnailEncoder.kt` (nuevo: bitmap Android).
- `android/app/src/main/java/pe/com/comparadorprecios/ui/AppNav.kt` (nuevo: rutas + barra inferior).
- `android/app/src/main/java/pe/com/comparadorprecios/ui/HistoryViewModel.kt` (nuevo).
- `android/app/src/main/java/pe/com/comparadorprecios/ui/DetailViewModel.kt` (nuevo).
- `android/app/src/main/java/pe/com/comparadorprecios/ui/HistoryScreens.kt` (nuevo: lista + detalle).
- `android/app/src/main/java/pe/com/comparadorprecios/ui/Theme.kt` (nuevo: tema M3 Expressive).
- `android/app/src/main/java/pe/com/comparadorprecios/MainActivity.kt` (modify: NavHost + tema + DB).
- `android/app/src/main/java/pe/com/comparadorprecios/ui/ScanScreen.kt` (modify en Task 7: emite miniatura).
- `android/app/src/main/java/pe/com/comparadorprecios/ui/Screens.kt` (modify en Task 6: reestilado).
- Tests: `history/HistoryRepositoryTest.kt`, `util/ThumbnailSpecTest.kt`, `ui/HistoryViewModelTest.kt` (bajo `app/src/test/...`).

---

### Task 1: Dependencias Room + KSP + Robolectric

**Files:**
- Modify: `android/build.gradle.kts` (agregar plugin KSP).
- Modify: `android/app/build.gradle.kts` (dependencias Room + tests).

**Interfaces:**
- Consumes: nada nuevo.
- Produces: capacidad de compilación KSP y Room lista para la Task 2.

- [ ] **Step 1: Agregar el plugin KSP al build raíz**

Edita el bloque `plugins` de `android/build.gradle.kts` (actual: application 8.5.2, kotlin.android 2.0.20, kotlin.plugin.compose 2.0.20, kotlin.plugin.serialization 2.0.20). Déjalo así:

```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20" apply false
    id("com.google.devtools.ksp") version "2.0.20-1.0.24" apply false
}
```

- [ ] **Step 2: Agregar dependencias en `android/app/build.gradle.kts`**

En el bloque `plugins` agrega `id("com.google.devtools.ksp")`. En `dependencies`, después del bloque de red, agrega:

```kotlin
    // Historial local — Room (solo dispositivo, sin nube).
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
```

Y en las dependencias `testImplementation`, agrega:

```kotlin
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("org.robolectric:robolectric:4.13")
```

- [ ] **Step 3: Sincronizar y verificar que compila**

Run: `.\gradlew.bat :app:assembleDebug` (desde `android/`, con `JAVA_HOME` al JDK 17 y `ANDROID_HOME` al SDK)
Expected: BUILD SUCCESSFUL (sin usar Room todavía; solo resuelve y procesa las dependencias).

- [ ] **Step 4: Commit**

```bash
git add android/build.gradle.kts android/app/build.gradle.kts
git commit -m "chore(android): add Room, KSP and Robolectric dependencies"
```

---

### Task 2: Entidad, DAO, base de datos y repositorio del historial

**Files:**
- Create: `android/app/src/main/java/pe/com/comparadorprecios/history/ScanRecord.kt`
- Create: `android/app/src/main/java/pe/com/comparadorprecios/history/HistoryDao.kt`
- Create: `android/app/src/main/java/pe/com/comparadorprecios/history/AppDatabase.kt`
- Create: `android/app/src/main/java/pe/com/comparadorprecios/history/HistoryItem.kt`
- Create: `android/app/src/main/java/pe/com/comparadorprecios/history/HistoryRepository.kt`
- Test: `android/app/src/test/java/pe/com/comparadorprecios/history/HistoryRepositoryTest.kt`

**Interfaces:**
- Consumes: `Identification`, `StoreResult` (de `data/ScanModels.kt`); `RetrofitProvider.json` (serializador kotlinx compartido).
- Produces: `ScanRecord`, `HistoryDao` (`upsert`, `observeAll`, `getById`, `deleteById`), `AppDatabase`, `HistoryItem(id, title, dateMillis, bestPrice, thumbnail)`, `HistoryRepository(all: Flow<List<HistoryItem>>, save(identification, tiendas, thumbnail): Long, get(id): HistoryItem?, delete(id): ScanRecord?, restore(record))` — usados por las Tasks 5 y 7.

- [ ] **Step 1: Escribir el test que falla**

Crea `android/app/src/test/java/pe/com/comparadorprecios/history/HistoryRepositoryTest.kt`:

```kotlin
package pe.com.comparadorprecios.history

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.StoreResult

@RunWith(RobolectricTestRunner::class)
class HistoryRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: HistoryRepository

    private val identification = Identification("Gloria", "Leche evaporada", "400g", "abarrotes", 0.92)
    private val tiendas = listOf(
        StoreResult("Wong", "encontrado", "Leche Evaporada Gloria 400g", 4.6, "https://www.wong.pe/p/1"),
        StoreResult("Plaza Vea", "encontrado", "Leche Evaporada Gloria 400g", 4.5, "https://www.plazavea.com.pe/p/1"),
    )

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = HistoryRepository(db.historyDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `save guarda y all lo devuelve con mejor precio`() = runTest {
        val id = repo.save(identification, tiendas, byteArrayOf(1, 2, 3))

        val all = repo.all.first()

        assertEquals(1, all.size)
        assertEquals(id, all[0].id)
        assertEquals("Gloria Leche evaporada 400g", all[0].title)
        assertEquals(4.5, all[0].bestPrice!!, 0.0)
        assertTrue(all[0].thumbnail.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `all ordena por recientes primero`() = runTest {
        repo.save(identification, tiendas, byteArrayOf(1), createdAt = 1000L)
        repo.save(identification, tiendas, byteArrayOf(2), createdAt = 2000L)

        val all = repo.all.first()

        assertEquals(2, all.size)
        assertTrue(all[0].thumbnail.contentEquals(byteArrayOf(2)))
    }

    @Test
    fun `delete y restore implementan deshacer`() = runTest {
        val id = repo.save(identification, tiendas, byteArrayOf(9))

        val deleted = repo.delete(id)
        assertEquals(0, repo.all.first().size)

        repo.restore(deleted!!)
        val all = repo.all.first()
        assertEquals(1, all.size)
        assertEquals(id, all[0].id)
    }

    @Test
    fun `get devuelve null para id inexistente`() = runTest {
        assertNull(repo.get(9999L))
    }
}
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "pe.com.comparadorprecios.history.*"`
Expected: FAIL — no se puede resolver `HistoryRepository` (no existe).

- [ ] **Step 3: Implementar la capa de datos**

Crea `history/ScanRecord.kt`:

```kotlin
package pe.com.comparadorprecios.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scans")
data class ScanRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val createdAt: Long,
    val marca: String,
    val nombre: String,
    val presentacion: String,
    val categoria: String,
    val confianza: Double,
    val tiendasJson: String,
    val bestPrice: Double?,
    val thumbnail: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScanRecord) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
```

Crea `history/HistoryDao.kt`:

```kotlin
package pe.com.comparadorprecios.history

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Upsert
    suspend fun upsert(record: ScanRecord): Long

    @Query("SELECT * FROM scans ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scans WHERE id = :id")
    suspend fun getById(id: Long): ScanRecord?

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

Crea `history/AppDatabase.kt`:

```kotlin
package pe.com.comparadorprecios.history

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ScanRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
```

Crea `history/HistoryItem.kt` (modelo de UI, sin detalles de Room):

```kotlin
package pe.com.comparadorprecios.history

import pe.com.comparadorprecios.data.StoreResult

data class HistoryItem(
    val id: Long,
    val title: String,
    val dateMillis: Long,
    val bestPrice: Double?,
    val thumbnail: ByteArray,
    val identification: pe.com.comparadorprecios.data.Identification,
    val tiendas: List<StoreResult>,
)
```

Crea `history/HistoryRepository.kt`:

```kotlin
package pe.com.comparadorprecios.history

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.RetrofitProvider
import pe.com.comparadorprecios.data.StoreResult

class HistoryRepository(private val dao: HistoryDao) {

    val all: Flow<List<HistoryItem>> =
        dao.observeAll().map { records -> records.map { it.toItem() } }

    suspend fun save(
        identification: Identification,
        tiendas: List<StoreResult>,
        thumbnail: ByteArray,
        createdAt: Long = System.currentTimeMillis(),
    ): Long {
        val record = ScanRecord(
            createdAt = createdAt,
            marca = identification.marca,
            nombre = identification.nombre,
            presentacion = identification.presentacion,
            categoria = identification.categoria,
            confianza = identification.confianza,
            tiendasJson = RetrofitProvider.json.encodeToString(ListSerializer(StoreResult.serializer()), tiendas),
            bestPrice = tiendas.filter { it.estado == "encontrado" }.minOfOrNull { it.precio ?: Double.MAX_VALUE },
            thumbnail = thumbnail,
        )
        return dao.upsert(record)
    }

    suspend fun get(id: Long): HistoryItem? = dao.getById(id)?.toItem()

    /** Borra y devuelve el registro para poder restaurarlo (deshacer). */
    suspend fun delete(id: Long): ScanRecord? {
        val record = dao.getById(id) ?: return null
        dao.deleteById(id)
        return record
    }

    suspend fun restore(record: ScanRecord) {
        dao.upsert(record)
    }

    private fun ScanRecord.toItem(): HistoryItem {
        val tiendas = RetrofitProvider.json.decodeFromString(ListSerializer(StoreResult.serializer()), tiendasJson)
        return HistoryItem(
            id = id,
            title = "$marca $nombre $presentacion".trim(),
            dateMillis = createdAt,
            bestPrice = bestPrice,
            thumbnail = thumbnail,
            identification = Identification(marca, nombre, presentacion, categoria, confianza),
            tiendas = tiendas,
        )
    }
}
```

- [ ] **Step 4: Ejecutar el test y verificar que pasa**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "pe.com.comparadorprecios.history.*"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/pe/com/comparadorprecios/history/ android/app/src/test/java/pe/com/comparadorprecios/history/
git commit -m "feat(android): add local scan history data layer with Room"
```

---

### Task 3: Miniatura (matemática pura + codificador Android)

**Files:**
- Create: `android/app/src/main/java/pe/com/comparadorprecios/util/ThumbnailSpec.kt`
- Create: `android/app/src/main/java/pe/com/comparadorprecios/util/ThumbnailEncoder.kt`
- Test: `android/app/src/test/java/pe/com/comparadorprecios/util/ThumbnailSpecTest.kt`

**Interfaces:**
- Consumes: nada (matemática) + `android.graphics.Bitmap` (solo el encoder).
- Produces: `ThumbnailSpec.compute(width, height): Pair<Int,Int>` (lado largo → 256, aspecto preservado), `ThumbnailSpec.Qualities = intArrayOf(85, 70, 55, 40)`, `ThumbnailSpec.MAX_BYTES = 200_000`, y `ThumbnailEncoder.encode(bitmap: Bitmap): ByteArray` — usado por la Task 7.

- [ ] **Step 1: Escribir el test que falla**

Crea `android/app/src/test/java/pe/com/comparadorprecios/util/ThumbnailSpecTest.kt`:

```kotlin
package pe.com.comparadorprecios.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailSpecTest {

    @Test
    fun `reduce el lado largo a 256 preservando aspecto`() {
        assertEquals(256 to 192, ThumbnailSpec.compute(1600, 1200))
        assertEquals(192 to 256, ThumbnailSpec.compute(1200, 1600))
    }

    @Test
    fun `no escala imagenes ya pequenas`() {
        assertEquals(200 to 100, ThumbnailSpec.compute(200, 100))
    }

    @Test
    fun `imagen cuadrada queda 256x256`() {
        assertEquals(256 to 256, ThumbnailSpec.compute(2000, 2000))
    }
}
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "pe.com.comparadorprecios.util.ThumbnailSpecTest"`
Expected: FAIL — no se puede resolver `ThumbnailSpec`.

- [ ] **Step 3: Implementar `util/ThumbnailSpec.kt` (puro JVM, sin imports de Android)**

```kotlin
package pe.com.comparadorprecios.util

/** Matemática de la miniatura del historial (spec: lado largo 256px, máx 200 KB). */
object ThumbnailSpec {
    const val LONG_SIDE_PX = 256
    const val MAX_BYTES = 200_000
    val Qualities = intArrayOf(85, 70, 55, 40)

    /** Devuelve (ancho, alto) destino preservando el aspecto. */
    fun compute(width: Int, height: Int): Pair<Int, Int> {
        val longest = maxOf(width, height)
        if (longest <= LONG_SIDE_PX) return width to height
        val ratio = LONG_SIDE_PX.toFloat() / longest
        return (width * ratio).toInt() to (height * ratio).toInt()
    }
}
```

- [ ] **Step 4: Implementar `util/ThumbnailEncoder.kt` (capa fina Android, verificada en Task 7)**

```kotlin
package pe.com.comparadorprecios.util

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/** Genera la miniatura JPEG del historial a partir del bitmap capturado. */
object ThumbnailEncoder {
    fun encode(original: Bitmap): ByteArray {
        val (targetW, targetH) = ThumbnailSpec.compute(original.width, original.height)
        val scaled = if (targetW == original.width && targetH == original.height) {
            original
        } else {
            Bitmap.createScaledBitmap(original, targetW, targetH, true)
        }
        try {
            for (quality in ThumbnailSpec.Qualities) {
                val bytes = jpeg(scaled, quality)
                if (bytes.size <= ThumbnailSpec.MAX_BYTES || quality == ThumbnailSpec.Qualities.last()) {
                    return bytes
                }
            }
            error("unreachable")
        } finally {
            if (scaled !== original) scaled.recycle()
        }
    }

    private fun jpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}
```

- [ ] **Step 5: Ejecutar el test y verificar que pasa**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "pe.com.comparadorprecios.util.ThumbnailSpecTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/pe/com/comparadorprecios/util/Thumbnail*.kt android/app/src/test/java/pe/com/comparadorprecios/util/ThumbnailSpecTest.kt
git commit -m "feat(android): add history thumbnail spec and encoder"
```

---

### Task 4: Navegación (NavHost + barra inferior + refactor de MainActivity)

**Files:**
- Create: `android/app/src/main/java/pe/com/comparadorprecios/ui/AppNav.kt`
- Modify: `android/app/src/main/java/pe/com/comparadorprecios/MainActivity.kt` (reemplazo del contenido `setContent` — ver Step 2)

**Interfaces:**
- Consumes: pantallas existentes (`ScanScreen`, `LoadingScreen`, `ResultScreen`, `LowConfidenceScreen`, `ErrorScreen`) y estados `ScanUiState` (sin cambios); `BuildConfig.SCAN_BASE_URL`, `ScanRepository` (existentes).
- Produces: `object AppRoutes` (`SCAN`, `HISTORY`, `detailRoute(id)`, `DETAIL_ARG`), composable `AppBottomBar` — usados por la Task 5 (destinos `history` y `detail/{scanId}`). El contenido de esos destinos se implementa en la Task 5; aquí se cablean con un `Text` temporal de una línea SOLO para verificar la navegación de punta a punta (la Task 5 lo reemplaza). Sin esto no se puede probar la barra inferior hasta el final.

- [ ] **Step 1: Crear `ui/AppNav.kt`**

```kotlin
package pe.com.comparadorprecios.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

object AppRoutes {
    const val SCAN = "scan"
    const val HISTORY = "history"
    const val DETAIL_ARG = "scanId"
    const val DETAIL = "detail/{$DETAIL_ARG}"
    fun detailRoute(id: Long): String = "detail/$id"
}

private data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

private val BOTTOM_DESTINATIONS = listOf(
    BottomDestination(AppRoutes.SCAN, "Escanear", Icons.Filled.PhotoCamera),
    BottomDestination(AppRoutes.HISTORY, "Historial", Icons.Filled.History),
)

@Composable
fun AppBottomBar(navController: NavController) {
    val backStack by navController.currentBackStackEntryAsState()
    // En el detalle se oculta la barra para dar aire a la comparación.
    val current = backStack?.destination?.route ?: AppRoutes.SCAN
    if (current == AppRoutes.DETAIL) return
    NavigationBar {
        BOTTOM_DESTINATIONS.forEach { dest ->
            NavigationBarItem(
                selected = current == dest.route,
                onClick = {
                    navController.navigate(dest.route) {
                        popUpTo(AppRoutes.SCAN) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(dest.icon, contentDescription = dest.label) },
                label = { Text(dest.label) },
            )
        }
    }
}
```

Nota: `Icons.Filled.PhotoCamera` e `Icons.Filled.History` viven en
`androidx.compose.material:material-icons-extended`. Si el import no resuelve,
agrega en la Task 1 (no requiere otro commit):
`implementation("androidx.compose.material:material-icons-extended")`.

- [ ] **Step 2: Refactorizar `MainActivity.kt` a NavHost**

Reemplaza el bloque `setContent` actual (máquina `when (val s = state)` sin
navegación) por esta estructura. El flujo de escaneo se mueve intacto al
destino `scan` (mismo `when`, mismos parámetros). Los destinos `history` y
`detail` quedan temporales hasta la Task 5:

```kotlin
setContent {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val context = LocalContext.current
            val repository = remember {
                ScanRepository(
                    RetrofitProvider.create(
                        baseUrl = BuildConfig.SCAN_BASE_URL,
                        debug = BuildConfig.DEBUG,
                    )
                )
            }
            val vm: ScanViewModel = viewModel { ScanViewModel(repository) }
            val navController = rememberNavController()
            Scaffold(
                bottomBar = { AppBottomBar(navController) },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = AppRoutes.SCAN,
                    modifier = Modifier.padding(padding),
                ) {
                    composable(AppRoutes.SCAN) {
                        // ... aquí va el when (val s = state) EXISTENTE, sin cambios ...
                    }
                    composable(AppRoutes.HISTORY) {
                        Text("Historial (Task 5)")
                    }
                    composable(
                        route = AppRoutes.DETAIL,
                        arguments = listOf(navArgument(AppRoutes.DETAIL_ARG) { type = NavType.LongType }),
                    ) {
                        Text("Detalle (Task 5)")
                    }
                }
            }
        }
    }
}
```

Imports nuevos en `MainActivity.kt`: `androidx.compose.foundation.layout.padding`,
`androidx.compose.material3.Scaffold`, `androidx.navigation.NavType`,
`androidx.navigation.compose.NavHost`, `androidx.navigation.compose.composable`,
`androidx.navigation.compose.rememberNavController`,
`androidx.navigation.navArgument`.

- [ ] **Step 3: Compilar y verificar navegación manual**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL. En el APK: la barra inferior muestra Escanear |
Historial; tocar Historial muestra el texto temporal; Volver regresa.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/pe/com/comparadorprecios/ui/AppNav.kt android/app/src/main/java/pe/com/comparadorprecios/MainActivity.kt
git commit -m "feat(android): add bottom-bar navigation with scan flow intact"
```

---

### Task 5: UI del historial (lista, detalle, borrado con deshacer)

**Files:**
- Create: `android/app/src/main/java/pe/com/comparadorprecios/ui/HistoryViewModel.kt`
- Create: `android/app/src/main/java/pe/com/comparadorprecios/ui/DetailViewModel.kt`
- Create: `android/app/src/main/java/pe/com/comparadorprecios/ui/HistoryScreens.kt`
- Modify: `android/app/src/main/java/pe/com/comparadorprecios/MainActivity.kt` (reemplaza los dos `Text` temporales por `HistoryScreen` y `DetailScreen`)
- Test: `android/app/src/test/java/pe/com/comparadorprecios/ui/HistoryViewModelTest.kt`

**Interfaces:**
- Consumes: `HistoryRepository`, `HistoryItem`, `ScanRecord` (Task 2); `AppRoutes.detailRoute` (Task 4); `ResultScreen` existente para reusar la comparación en el detalle.
- Produces: destinos `history` y `detail` funcionales — consumidos por la Task 7 (guardado al escanear).

- [ ] **Step 1: Escribir el test que falla**

Crea `android/app/src/test/java/pe/com/comparadorprecios/ui/HistoryViewModelTest.kt`:

```kotlin
package pe.com.comparadorprecios.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.StoreResult
import pe.com.comparadorprecios.history.AppDatabase
import pe.com.comparadorprecios.history.HistoryRepository

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HistoryViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: HistoryRepository
    private val dispatcher = StandardTestDispatcher()

    private val identification = Identification("Gloria", "Leche evaporada", "400g", "abarrotes", 0.92)
    private val tiendas = listOf(
        StoreResult("Wong", "encontrado", "Leche Evaporada Gloria 400g", 4.6, "https://www.wong.pe/p/1"),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = HistoryRepository(db.historyDao())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `items expone el historial ordenado`() = runTest(dispatcher) {
        repo.save(identification, tiendas, byteArrayOf(1), createdAt = 1000L)
        repo.save(identification, tiendas, byteArrayOf(2), createdAt = 2000L)

        val vm = HistoryViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()

        val items = vm.items.first()
        assertEquals(2, items.size)
        assertTrue(items[0].thumbnail.contentEquals(byteArrayOf(2)))
    }

    @Test
    fun `requestDelete + undo restauran el registro`() = runTest(dispatcher) {
        val id = repo.save(identification, tiendas, byteArrayOf(7))

        val vm = HistoryViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()
        vm.requestDelete(id)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, vm.items.first().size)
        assertTrue(vm.showUndo.first())

        vm.undo()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.items.first().size)
    }
}
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "pe.com.comparadorprecios.ui.HistoryViewModelTest"`
Expected: FAIL — no se puede resolver `HistoryViewModel`.

- [ ] **Step 3: Implementar los ViewModels**

Crea `ui/HistoryViewModel.kt`:

```kotlin
package pe.com.comparadorprecios.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.com.comparadorprecios.history.HistoryItem
import pe.com.comparadorprecios.history.HistoryRepository
import pe.com.comparadorprecios.history.ScanRecord

class HistoryViewModel(private val repository: HistoryRepository) : ViewModel() {

    val items: StateFlow<List<HistoryItem>> =
        repository.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showUndo = MutableStateFlow(false)
    val showUndo: StateFlow<Boolean> = _showUndo.asStateFlow()

    private var lastDeleted: ScanRecord? = null

    fun requestDelete(id: Long) {
        viewModelScope.launch {
            lastDeleted = repository.delete(id)
            _showUndo.value = lastDeleted != null
        }
    }

    fun undo() {
        viewModelScope.launch {
            lastDeleted?.let { repository.restore(it) }
            lastDeleted = null
            _showUndo.value = false
        }
    }

    fun dismissUndo() {
        lastDeleted = null
        _showUndo.value = false
    }
}
```

Crea `ui/DetailViewModel.kt`:

```kotlin
package pe.com.comparadorprecios.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.com.comparadorprecios.history.HistoryItem
import pe.com.comparadorprecios.history.HistoryRepository

class DetailViewModel(
    private val repository: HistoryRepository,
    private val scanId: Long,
) : ViewModel() {

    private val _item = MutableStateFlow<HistoryItem?>(null)
    val item: StateFlow<HistoryItem?> = _item.asStateFlow()

    init {
        viewModelScope.launch {
            _item.value = repository.get(scanId)
        }
    }
}
```

- [ ] **Step 4: Implementar `ui/HistoryScreens.kt`**

```kotlin
package pe.com.comparadorprecios.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.com.comparadorprecios.history.HistoryItem
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

private val soles = NumberFormat.getCurrencyInstance(Locale("es", "PE"))

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    snackbar: SnackbarHostState,
    onOpen: (Long) -> Unit,
) {
    val items by viewModel.items.collectAsState()
    val showUndo by viewModel.showUndo.collectAsState()
    var pendingDelete by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(showUndo) {
        if (showUndo) {
            val result = snackbar.showSnackbar("Escaneo eliminado", actionLabel = "Deshacer")
            if (result == SnackbarResult.ActionPerformed) viewModel.undo()
            else viewModel.dismissUndo()
        }
    }

    if (items.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Aún no tienes escaneos", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Escanea tu primer producto desde la pestaña Escanear.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { item ->
            HistoryRow(
                item = item,
                onOpen = { onOpen(item.id) },
                onDelete = { pendingDelete = item.id },
            )
        }
    }

    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar escaneo") },
            text = { Text("Se borrará de tu historial en este dispositivo.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    viewModel.requestDelete(id)
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val bitmap = remember(item.id) {
                BitmapFactory.decodeByteArray(item.thumbnail, 0, item.thumbnail.size)
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = item.title,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Bold)
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.dateMillis)),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (item.bestPrice != null) "Desde ${soles.format(item.bestPrice)}" else "Sin precios",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
            }
        }
    }
}

/** Detalle: reusa la comparación existente con los datos guardados. */
@Composable
fun DetailScreen(item: HistoryItem, onBack: () -> Unit) {
    ResultScreen(
        identification = item.identification,
        tiendas = item.tiendas,
        onNewScan = onBack,
    )
}
```

Nota: `Icons.Filled.Delete` también es de `material-icons-extended` (ver nota de
la Task 4). `ResultScreen` es el composable existente de `Screens.kt` (mismos
parámetros: `identification`, `tiendas`, `onNewScan`).

- [ ] **Step 5: Cablear los destinos en `MainActivity.kt`**

Reemplaza `Text("Historial (Task 5)")` por:

```kotlin
composable(AppRoutes.HISTORY) {
    val historyVm: HistoryViewModel = viewModel { HistoryViewModel(historyRepository) }
    HistoryScreen(
        viewModel = historyVm,
        snackbar = snackbar,
        onOpen = { id -> navController.navigate(AppRoutes.detailRoute(id)) },
    )
}
```

Reemplaza `Text("Detalle (Task 5)")` por:

```kotlin
composable(
    route = AppRoutes.DETAIL,
    arguments = listOf(navArgument(AppRoutes.DETAIL_ARG) { type = NavType.LongType }),
) { backStackEntry ->
    val scanId = backStackEntry.arguments?.getLong(AppRoutes.DETAIL_ARG) ?: return@composable
    val detailVm: DetailViewModel = viewModel { DetailViewModel(historyRepository, scanId) }
    val item by detailVm.item.collectAsState()
    val loaded = item
    if (loaded != null) {
        DetailScreen(item = loaded, onBack = { navController.popBackStack() })
    } else {
        LoadingScreen()
    }
}
```

Requisitos para este step: en `MainActivity.kt`, antes del `NavHost`, crear
`historyRepository` con `remember` usando Room:

```kotlin
val historyRepository = remember {
    val db = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "comparador-precios.db",
    ).build()
    HistoryRepository(db.historyDao())
}
```

Y un `SnackbarHostState` + `SnackbarHost` en el `Scaffold`:

```kotlin
val snackbar = remember { SnackbarHostState() }
Scaffold(
    bottomBar = { AppBottomBar(navController) },
    snackbarHost = { SnackbarHost(snackbar) },
) { padding -> /* NavHost igual, con Modifier.padding(padding) */ }
```

Imports nuevos: `androidx.compose.material3.SnackbarHost`,
`androidx.compose.material3.SnackbarHostState`, `androidx.room.Room`,
`pe.com.comparadorprecios.history.AppDatabase`,
`pe.com.comparadorprecios.history.HistoryRepository`,
`androidx.compose.runtime.collectAsState`, `androidx.compose.runtime.getValue`.

- [ ] **Step 6: Ejecutar los tests y compilar**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "pe.com.comparadorprecios.ui.HistoryViewModelTest"`
Expected: PASS (2 tests).
Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/pe/com/comparadorprecios/ui/History*.kt android/app/src/main/java/pe/com/comparadorprecios/ui/Detail*.kt android/app/src/main/java/pe/com/comparadorprecios/MainActivity.kt android/app/src/test/java/pe/com/comparadorprecios/ui/HistoryViewModelTest.kt
git commit -m "feat(android): add history catalog with detail and undo delete"
```

---

### Task 6: Rediseño M3 Expressive (tema + reestilado de pantallas existentes)

**Files:**
- Create: `android/app/src/main/java/pe/com/comparadorprecios/ui/Theme.kt`
- Modify: `android/app/src/main/java/pe/com/comparadorprecios/MainActivity.kt` (`MaterialTheme` → `ComparadorTheme`)
- Modify: `android/app/src/main/java/pe/com/comparadorprecios/ui/Screens.kt` (tarjetas elevadas + jerarquía de precio)
- Modify: `android/app/src/main/java/pe/com/comparadorprecios/ui/ScanScreen.kt` (botón captura → FAB extendido)

**Interfaces:**
- Consumes: nada nuevo.
- Produces: `ComparadorTheme` — usado por `MainActivity` (y por `HistoryScreens` de la Task 5 sin cambios). Ningún cambio de comportamiento ni de firmas.

- [ ] **Step 1: Crear `ui/Theme.kt`**

```kotlin
package pe.com.comparadorprecios.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val FallbackLight = lightColorScheme()
private val FallbackDark = darkColorScheme()

private val ExpressiveShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

/** Tema M3 Expressive: color dinámico en Android 12+, fallback estático (minSdk 26). */
@Composable
fun ComparadorTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) FallbackDark else FallbackLight
    }
    MaterialTheme(colorScheme = scheme, shapes = ExpressiveShapes, content = content)
}
```

- [ ] **Step 2: Usar el tema en `MainActivity.kt`**

Reemplaza (2 ocurrencias: import y uso):

```kotlin
import androidx.compose.material3.MaterialTheme
```

por:

```kotlin
import pe.com.comparadorprecios.ui.ComparadorTheme
```

y:

```kotlin
            MaterialTheme {
```

por:

```kotlin
            ComparadorTheme {
```

(Ojo: `Screens.kt` y `HistoryScreens.kt` siguen usando `MaterialTheme.typography`/
`MaterialTheme.colorScheme` para leer el tema — eso no cambia.)

- [ ] **Step 3: Tarjetas elevadas y jerarquía de precio en `Screens.kt`**

Reemplaza el import:

```kotlin
import androidx.compose.material3.Card
```

por:

```kotlin
import androidx.compose.material3.ElevatedCard
```

Reemplaza en `StoreRow`:

```kotlin
    Card(modifier = Modifier.fillMaxWidth()) {
```

por:

```kotlin
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
```

Reemplaza el precio destacado:

```kotlin
                    "encontrado" -> Text(
                        soles.format(result.precio ?: 0.0),
                        fontWeight = FontWeight.Bold,
                    )
```

por:

```kotlin
                    "encontrado" -> Text(
                        soles.format(result.precio ?: 0.0),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
```

Y el título del resultado:

```kotlin
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
```

por:

```kotlin
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
```

- [ ] **Step 4: FAB extendido en `ScanScreen.kt`**

El bloque actual del botón (al final del `Box` en `ScanScreen`):

```kotlin
        Button(
            onClick = {
                if (capturing) return@Button
                capturing = true
                capturePhoto(context, imageCapture, onImageCaptured, onError) { capturing = false }
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            enabled = !capturing,
        ) {
            Text(if (capturing) "Capturando…" else "Escanear producto")
        }
```

Reemplázalo por:

```kotlin
        ExtendedFloatingActionButton(
            onClick = {
                if (capturing) return@ExtendedFloatingActionButton
                capturing = true
                capturePhoto(context, imageCapture, onImageCaptured, onError) { capturing = false }
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
            text = { Text(if (capturing) "Capturando…" else "Escanear producto") },
        )
```

Agrega los imports (`Icons.Filled.PhotoCamera` es de `material-icons-extended`,
ver nota de la Task 4):

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
```

y elimina el import de `Button` (`androidx.compose.material3.Button`) solo si
queda sin otro uso en ese archivo (si compila con él, déjalo).

- [ ] **Step 5: Compilar**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL, sin cambios de comportamiento (mismas pantallas,
nuevo aspecto).

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/pe/com/comparadorprecios/ui/Theme.kt android/app/src/main/java/pe/com/comparadorprecios/MainActivity.kt android/app/src/main/java/pe/com/comparadorprecios/ui/Screens.kt android/app/src/main/java/pe/com/comparadorprecios/ui/ScanScreen.kt
git commit -m "feat(android): apply Material 3 Expressive theme and restyle"
```

---

### Task 7: Guardado al escanear + verificación final

**Files:**
- Modify: `android/app/src/main/java/pe/com/comparadorprecios/ui/ScanScreen.kt` (emite miniatura junto al data URI).
- Modify: `android/app/src/main/java/pe/com/comparadorprecios/MainActivity.kt` (guarda cada `Success` en Room).
- Modify: `android/README.md` (documenta navegación, historial y decisiones).

**Interfaces:**
- Consumes: `ThumbnailEncoder` (Task 3), `HistoryRepository` (Task 2, ya instanciado en MainActivity desde la Task 5), `ScanUiState.Success` (existente).
- Produces: historial que se llena solo al escanear; nada nuevo hacia otras tasks (última).

- [ ] **Step 1: Emitir la miniatura desde `ScanScreen.kt`**

Cambia la firma del composable:

```kotlin
fun ScanScreen(onImageCaptured: (dataUri: String) -> Unit, onError: (String) -> Unit) {
```

por:

```kotlin
fun ScanScreen(
    onImageCaptured: (dataUri: String, thumbnail: ByteArray?) -> Unit,
    onError: (String) -> Unit,
) {
```

Cambia la firma privada de `capturePhoto`:

```kotlin
private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onImageCaptured: (String) -> Unit,
    onError: (String) -> Unit,
    onDone: () -> Unit,
) {
```

por:

```kotlin
private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onImageCaptured: (String, ByteArray?) -> Unit,
    onError: (String) -> Unit,
    onDone: () -> Unit,
) {
```

Y el bloque `onImageSaved` actual:

```kotlin
                try {
                    val bytes = file.readBytes()
                    val bitmap = ImageEncoding.decode(bytes)
                    val payload = if (bitmap != null) {
                        ImageEncoding.compressToLimit(bitmap)
                    } else {
                        bytes
                    }
                    val dataUri = ImageEncoding.toDataUri(payload)
                    if (ImageEncoding.exceedsLimit(dataUri)) {
                        onError("La foto es demasiado grande incluso comprimida. Acércate menos o baja la resolución.")
                    } else {
                        onImageCaptured(dataUri)
                    }
                } catch (e: Exception) {
```

por (la miniatura se deriva del mismo bitmap; si falla, se envía `null` y el
escaneo continúa sin guardarse — el guardado nunca bloquea el resultado):

```kotlin
                try {
                    val bytes = file.readBytes()
                    val bitmap = ImageEncoding.decode(bytes)
                    val payload = if (bitmap != null) {
                        ImageEncoding.compressToLimit(bitmap)
                    } else {
                        bytes
                    }
                    val dataUri = ImageEncoding.toDataUri(payload)
                    if (ImageEncoding.exceedsLimit(dataUri)) {
                        onError("La foto es demasiado grande incluso comprimida. Acércate menos o baja la resolución.")
                    } else {
                        val thumbnail = if (bitmap != null) {
                            runCatching { ThumbnailEncoder.encode(bitmap) }.getOrNull()
                        } else {
                            null
                        }
                        onImageCaptured(dataUri, thumbnail)
                    }
                } catch (e: Exception) {
```

Agrega el import `pe.com.comparadorprecios.util.ThumbnailEncoder`.

- [ ] **Step 2: Guardar cada `Success` en `MainActivity.kt`**

En el destino `scan`, agrega estado para la miniatura pendiente junto a los
`remember` existentes (`captureError`, `manualName`):

```kotlin
                    var pendingThumbnail by remember { mutableStateOf<ByteArray?>(null) }
```

Actualiza la llamada a `ScanScreen`:

```kotlin
                                ScanScreen(
                                    onImageCaptured = { vm.scan(it) },
                                    onError = { captureError = it },
                                )
```

por:

```kotlin
                                ScanScreen(
                                    onImageCaptured = { uri, thumbnail ->
                                        pendingThumbnail = thumbnail
                                        vm.scan(uri)
                                    },
                                    onError = { captureError = it },
                                )
```

Y después del `when (val s = state)` del destino `scan` (al mismo nivel,
dentro del `composable(AppRoutes.SCAN)`), agrega el guardado en segundo plano.
`LaunchedEffect` con `runCatching`: si Room falla, el resultado ya está en
pantalla y no pasa nada (constraint del spec):

```kotlin
                    LaunchedEffect(s) {
                        if (s is ScanUiState.Success) {
                            val thumbnail = pendingThumbnail
                            pendingThumbnail = null
                            if (thumbnail != null) {
                                runCatching {
                                    historyRepository.save(s.identification, s.tiendas, thumbnail)
                                }
                            }
                        }
                    }
```

Agrega el import `androidx.compose.runtime.LaunchedEffect`.

- [ ] **Step 3: Suite completa en verde**

Run: `npm test` (desde la raíz del repo)
Expected: PASS — 46 tests del backend, sin regresiones (esta task no toca backend).
Run: `.\gradlew.bat :app:testDebugUnitTest` (desde `android/`)
Expected: PASS — 16 tests existentes + 4 (Task 2) + 3 (Task 3) + 2 (Task 5) = 25 tests.
Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Verificación manual en dispositivo físico**

1. `adb install -r app\build\outputs\apk\debug\app-debug.apk`.
2. Escanea un producto real → aparece en Historial al volver a la pestaña.
3. Toca el registro → detalle idéntico al resultado, con links que abren.
4. Borra con confirmación → Snackbar Deshacer restaura; sin deshacer, desaparece.
5. Mata la app y reábrela → el historial persiste (Room).
6. Rota la pantalla en lista y detalle → sin crashes ni duplicados.

- [ ] **Step 5: Actualizar `android/README.md`**

Agrega una sección `## Historial` con: Room (`comparador-precios.db`, solo
dispositivo), miniatura 256px/máx 200 KB (nunca la foto completa), baja
confianza no se guarda, y que desinstalar la app borra el historial.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/pe/com/comparadorprecios/ui/ScanScreen.kt android/app/src/main/java/pe/com/comparadorprecios/MainActivity.kt android/README.md
git commit -m "feat(android): persist successful scans to local history"
```
