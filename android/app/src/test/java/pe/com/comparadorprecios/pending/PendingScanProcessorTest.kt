package pe.com.comparadorprecios.pending

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.ScanError
import pe.com.comparadorprecios.data.ScanRepository
import pe.com.comparadorprecios.data.ScanResponse
import pe.com.comparadorprecios.data.StoreResult
import pe.com.comparadorprecios.history.AppDatabase
import pe.com.comparadorprecios.history.HistoryRepository
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PendingScanProcessorTest {

    private lateinit var db: AppDatabase
    private lateinit var pending: PendingScanRepository
    private lateinit var history: HistoryRepository
    private lateinit var processor: PendingScanProcessor
    private lateinit var imagesDir: File
    private val scans: ScanRepository = mock()

    private val identification = Identification("Gloria", "Leche evaporada", "400g", "abarrotes", 0.92, "leche evaporada")
    private val response = ScanResponse(
        identification,
        listOf(StoreResult("Metro", "encontrado", "Leche Evaporada Gloria 400g", 4.5, "https://metro.pe/p/1")),
    )
    private val imageDataUri = "data:image/jpeg;base64,ABC123"

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        imagesDir = File(context.cacheDir, "test-pending-${System.nanoTime()}")
        pending = PendingScanRepository(db.pendingScanDao(), imagesDir)
        history = HistoryRepository(db.historyDao())
        processor = PendingScanProcessor(pending, scans, history)
    }

    @After
    fun tearDown() {
        db.close()
        imagesDir.deleteRecursively()
    }

    @Test
    fun `encolar guarda la imagen en disco y no en la base de datos`() = runTest {
        pending.enqueue(imageDataUri, byteArrayOf(1, 2), categoria = "abarrotes")

        val scan = pending.current().single()
        assertEquals("abarrotes", scan.categoria)
        assertTrue(File(scan.imagePath).exists())
        assertEquals(imageDataUri, pending.readImage(scan))
    }

    @Test
    fun `procesa el pendiente, lo manda al historial y limpia la cola y el archivo`() = runTest {
        whenever(scans.scan(eq(imageDataUri), anyOrNull(), eq("abarrotes"))).thenReturn(response)
        pending.enqueue(imageDataUri, byteArrayOf(1, 2), categoria = "abarrotes", createdAt = 1_000L)
        val imagePath = pending.current().single().imagePath

        val result = processor.processAll(saveBattery = false)

        assertEquals(1, result.processed)
        assertTrue(pending.current().isEmpty())
        assertFalse(File(imagePath).exists())

        val saved = history.all.first().single()
        assertEquals("Gloria Leche evaporada 400g", saved.title)
        // La fecha es la de la foto, no la del procesamiento.
        assertEquals(1_000L, saved.dateMillis)
        assertEquals(4.5, saved.bestPrice!!, 0.0)
    }

    @Test
    fun `si sigue sin haber red la cola queda intacta para reintentar`() = runTest {
        whenever(scans.scan(any(), anyOrNull(), anyOrNull())).thenThrow(ScanError.Network("sin conexión"))
        pending.enqueue(imageDataUri, null, categoria = null)

        val result = processor.processAll(saveBattery = false)

        assertTrue(result.interruptedByNetwork)
        assertEquals(0, result.processed)
        assertEquals(1, pending.current().size)
        assertTrue(history.all.first().isEmpty())
    }

    @Test
    fun `una foto que el backend rechaza se marca con el error y no se pierde`() = runTest {
        whenever(scans.scan(any(), anyOrNull(), anyOrNull()))
            .thenThrow(ScanError.IdentificationFailed("No se pudo identificar el producto."))
        pending.enqueue(imageDataUri, null, categoria = null)

        val result = processor.processAll(saveBattery = false)

        assertEquals(1, result.failed)
        val scan = pending.current().single()
        assertNotNull(scan.lastError)
        assertTrue(File(scan.imagePath).exists())
    }

    @Test
    fun `sesion expirada detiene la cola y lo reporta para pedir login`() = runTest {
        whenever(scans.scan(any(), anyOrNull(), anyOrNull())).thenThrow(ScanError.Unauthorized("expiró"))
        pending.enqueue(imageDataUri, null, categoria = null)
        pending.enqueue(imageDataUri, null, categoria = null)

        val result = processor.processAll(saveBattery = false)

        assertTrue(result.needsSignIn)
        // No se intenta con el resto: seguiría fallando igual.
        assertEquals(2, pending.current().size)
    }

    @Test
    fun `descarta la fila si el archivo de la foto ya no existe`() = runTest {
        pending.enqueue(imageDataUri, null, categoria = null)
        File(pending.current().single().imagePath).delete()

        val result = processor.processAll(saveBattery = false)

        assertEquals(0, result.processed)
        assertEquals(0, result.failed)
        assertTrue(pending.current().isEmpty())
    }
}
