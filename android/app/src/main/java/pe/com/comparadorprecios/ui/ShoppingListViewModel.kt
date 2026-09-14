package pe.com.comparadorprecios.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.com.comparadorprecios.data.DeviceContextPayload
import pe.com.comparadorprecios.data.ScanError
import pe.com.comparadorprecios.data.ScanRepository
import pe.com.comparadorprecios.shopping.BasketLine
import pe.com.comparadorprecios.shopping.ShoppingBasket
import pe.com.comparadorprecios.shopping.ShoppingLine
import pe.com.comparadorprecios.shopping.ShoppingListRepository
import pe.com.comparadorprecios.shopping.StoreBasket

data class ShoppingListContent(
    val lines: List<ShoppingLine> = emptyList(),
    val baskets: List<StoreBasket> = emptyList(),
)

class ShoppingListViewModel(
    private val shopping: ShoppingListRepository,
    private val scanRepository: ScanRepository,
) : ViewModel() {

    val content: StateFlow<ShoppingListContent> = shopping.lines
        .map { lines ->
            ShoppingListContent(
                lines = lines,
                baskets = ShoppingBasket.compare(lines.map { BasketLine(it.title, it.quantity, it.tiendas) }),
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ShoppingListContent())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setQuantity(id: Long, quantity: Int) {
        viewModelScope.launch { shopping.setQuantity(id, quantity) }
    }

    fun remove(id: Long) {
        viewModelScope.launch { shopping.remove(id) }
    }

    fun dismissMessage() {
        _message.value = null
    }

    fun refreshPrices(saveBattery: Boolean) {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                val lines = shopping.current()
                val failures = lines.map { line ->
                    async {
                        try {
                            val response = scanRepository.prices(line.identification, DeviceContextPayload(saveBattery))
                            shopping.updatePrices(line.id, response.tiendas)
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            e
                        }
                    }
                }.awaitAll().filterNotNull()

                _message.value = when {
                    failures.any { it is ScanError.Unauthorized } -> "Tu sesión expiró. Vuelve a iniciar sesión para actualizar precios."
                    failures.isNotEmpty() -> "No se pudieron actualizar ${failures.size} de ${lines.size} productos. Reintenta."
                    lines.isNotEmpty() -> "Precios actualizados."
                    else -> null
                }
            } finally {
                _refreshing.value = false
            }
        }
    }
}
