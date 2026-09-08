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
        repository.all.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
