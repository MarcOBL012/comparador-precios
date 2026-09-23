package pe.com.comparadorprecios.pending

import kotlinx.coroutines.CancellationException
import pe.com.comparadorprecios.data.DeviceContextPayload
import pe.com.comparadorprecios.data.ScanError
import pe.com.comparadorprecios.data.ScanRepository
import pe.com.comparadorprecios.history.HistoryRepository
import pe.com.comparadorprecios.util.PriceSorting

/**
 * Vacía la cola de escaneos pendientes cuando hay conexión: identifica cada foto, compara precios
 * y la mueve al historial. Es la contraparte de [PendingScanRepository.enqueue].
 */
class PendingScanProcessor(
    private val pending: PendingScanRepository,
    private val scans: ScanRepository,
    private val history: HistoryRepository,
) {
    data class Result(
        val processed: Int = 0,
        val failed: Int = 0,
        /** La red volvió a caerse a mitad de la cola: lo que queda se reintenta más tarde. */
        val interruptedByNetwork: Boolean = false,
        /** La sesión expiró: hay que iniciar sesión otra vez antes de reintentar. */
        val needsSignIn: Boolean = false,
    ) {
        val touched: Boolean get() = processed > 0 || failed > 0
    }

    suspend fun processAll(saveBattery: Boolean): Result {
        var result = Result()
        for (scan in pending.current()) {
            val imageDataUri = pending.readImage(scan)
            if (imageDataUri == null) {
                // El archivo ya no existe; la fila sin imagen no sirve para nada.
                pending.remove(scan.id)
                continue
            }
            try {
                val response = scans.scan(imageDataUri, DeviceContextPayload(saveBattery), scan.categoria)
                history.save(
                    identification = response.identification,
                    tiendas = PriceSorting.sortForDisplay(response.tiendas),
                    thumbnail = scan.thumbnail ?: ByteArray(0),
                    // Se conserva la fecha de la foto, no la del procesamiento.
                    createdAt = scan.createdAt,
                )
                pending.remove(scan.id)
                result = result.copy(processed = result.processed + 1)
            } catch (e: ScanError.Network) {
                // Sigue sin haber red: se deja la cola intacta para el próximo intento.
                return result.copy(interruptedByNetwork = true)
            } catch (e: ScanError.Unauthorized) {
                pending.markError(scan.id, "Tu sesión expiró. Inicia sesión y reintenta.")
                return result.copy(failed = result.failed + 1, needsSignIn = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Foto ilegible o error del servidor: se marca y se sigue con las demás, sin
                // borrarla — el usuario decide si la reintenta o la descarta.
                pending.markError(scan.id, e.message ?: "No se pudo procesar esta foto.")
                result = result.copy(failed = result.failed + 1)
            }
        }
        return result
    }
}
