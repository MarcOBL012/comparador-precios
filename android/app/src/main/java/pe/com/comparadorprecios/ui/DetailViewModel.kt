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
