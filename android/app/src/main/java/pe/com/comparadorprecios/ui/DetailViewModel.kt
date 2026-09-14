package pe.com.comparadorprecios.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.com.comparadorprecios.data.DeviceContextPayload
import pe.com.comparadorprecios.data.ScanError
import pe.com.comparadorprecios.data.ScanRepository
import pe.com.comparadorprecios.history.HistoryItem
import pe.com.comparadorprecios.history.HistoryRepository
import pe.com.comparadorprecios.history.PriceCheckSnapshot

class DetailViewModel(
    private val repository: HistoryRepository,
    private val scanRepository: ScanRepository,
    private val scanId: Long,
) : ViewModel() {

    private val _item = MutableStateFlow<HistoryItem?>(null)
    val item: StateFlow<HistoryItem?> = _item.asStateFlow()

    val latestCheck: StateFlow<PriceCheckSnapshot?> =
        repository.latestCheck(scanId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _checkError = MutableStateFlow<String?>(null)
    val checkError: StateFlow<String?> = _checkError.asStateFlow()

    init {
        viewModelScope.launch {
            _item.value = repository.get(scanId)
        }
    }

    fun checkPrices(saveBattery: Boolean) {
        val current = _item.value ?: return
        if (_checking.value) return
        _checking.value = true
        _checkError.value = null
        viewModelScope.launch {
            try {
                val response = scanRepository.prices(current.identification, DeviceContextPayload(saveBattery))
                repository.saveCheck(scanId, response.tiendas)
            } catch (e: ScanError) {
                _checkError.value = e.message ?: "No se pudo revisar el precio. Reintenta."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _checkError.value = "No se pudo revisar el precio. Reintenta."
            } finally {
                _checking.value = false
            }
        }
    }
}
