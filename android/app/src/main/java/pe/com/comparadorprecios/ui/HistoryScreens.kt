package pe.com.comparadorprecios.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import pe.com.comparadorprecios.data.StoreResult
import pe.com.comparadorprecios.history.HistoryItem
import pe.com.comparadorprecios.history.PriceChange
import pe.com.comparadorprecios.history.PriceCheckSnapshot
import pe.com.comparadorprecios.history.PriceTrend
import pe.com.comparadorprecios.pending.PendingScan
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    snackbar: SnackbarHostState,
    onOpen: (Long) -> Unit,
    pendingScans: List<PendingScan> = emptyList(),
    processingPending: Boolean = false,
    offline: Boolean = false,
    onProcessPending: () -> Unit = {},
    onDiscardPending: (Long) -> Unit = {},
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

    if (items.isEmpty() && pendingScans.isEmpty()) {
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
        if (pendingScans.isNotEmpty()) {
            item(key = "pending-header") {
                PendingHeader(
                    count = pendingScans.size,
                    processing = processingPending,
                    offline = offline,
                    onProcessPending = onProcessPending,
                )
            }
            items(pendingScans, key = { "pending-${it.id}" }) { scan ->
                PendingRow(scan = scan, onDiscard = { onDiscardPending(scan.id) })
            }
            if (items.isNotEmpty()) {
                item(key = "history-header") {
                    Text(
                        "Procesados",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
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
private fun PendingHeader(count: Int, processing: Boolean, offline: Boolean, onProcessPending: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CloudOff, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (count == 1) "1 escaneo pendiente" else "$count escaneos pendientes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                when {
                    processing -> "Procesando…"
                    offline -> "Se procesarán automáticamente cuando vuelva el internet."
                    else -> "Hay conexión: se están procesando solos. También puedes forzarlo."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (processing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (!offline) {
                OutlinedButton(onClick = onProcessPending, modifier = Modifier.fillMaxWidth()) {
                    Text("Procesar ahora")
                }
            }
        }
    }
}

@Composable
private fun PendingRow(scan: PendingScan, onDiscard: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val bitmap = remember(scan.id) {
                scan.thumbnail?.takeIf { it.isNotEmpty() }?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Foto pendiente",
                    modifier = Modifier.size(56.dp),
                )
            } else {
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.CloudOff, contentDescription = null) }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Pendiente de procesar", fontWeight = FontWeight.Bold)
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(scan.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                )
                scan.categoria?.let {
                    Text("Categoría: ${categoryLabel(it)}", style = MaterialTheme.typography.bodySmall)
                }
                scan.lastError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            IconButton(onClick = onDiscard) {
                Icon(Icons.Filled.Delete, contentDescription = "Descartar foto pendiente")
            }
        }
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
                    if (item.bestPrice != null) "Desde ${formatSoles(item.bestPrice)}" else "Sin precios",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (item.priceDropped) {
                    Spacer(Modifier.height(4.dp))
                    Pill(
                        text = "▼ Bajó a ${formatSoles(item.latestBestPrice!!)}",
                        container = MaterialTheme.colorScheme.primary,
                        content = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
            }
        }
    }
}

/** Detalle: la comparación guardada más la revisión de precio contra el día del escaneo. */
@Composable
fun DetailScreen(
    item: HistoryItem,
    latestCheck: PriceCheckSnapshot?,
    checking: Boolean,
    checkError: String?,
    canCheck: Boolean,
    onCheckPrices: () -> Unit,
    onAddToList: (tiendas: List<StoreResult>) -> Unit,
    onBack: () -> Unit,
) {
    val currentTiendas = latestCheck?.tiendas ?: item.tiendas
    ResultScreen(
        identification = item.identification,
        tiendas = item.tiendas,
        onNewScan = onBack,
        newScanLabel = "Volver al historial",
        onAddToList = { onAddToList(currentTiendas) },
        extraContent = {
            item(key = "price-check") {
                PriceCheckCard(
                    scannedAt = item.dateMillis,
                    original = item.tiendas,
                    latestCheck = latestCheck,
                    checking = checking,
                    checkError = checkError,
                    canCheck = canCheck,
                    onCheckPrices = onCheckPrices,
                )
            }
        },
    )
}

@Composable
private fun PriceCheckCard(
    scannedAt: Long,
    original: List<StoreResult>,
    latestCheck: PriceCheckSnapshot?,
    checking: Boolean,
    checkError: String?,
    canCheck: Boolean,
    onCheckPrices: () -> Unit,
) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("¿Cambió el precio?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (latestCheck == null) {
                Text(
                    "Los precios de arriba son del ${dateFormat.format(Date(scannedAt))}. Revisa cuánto cuesta hoy.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "Escaneado el ${dateFormat.format(Date(scannedAt))} · revisado el ${dateFormat.format(Date(latestCheck.checkedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                )
                val changes = PriceTrend.compare(original, latestCheck.tiendas)
                if (changes.isEmpty()) {
                    Text("Hoy ninguna tienda tiene este producto.")
                }
                changes.forEach { PriceChangeRow(it) }
            }
            checkError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = onCheckPrices,
                enabled = canCheck && !checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        checking -> "Revisando…"
                        !canCheck -> "Sin conexión"
                        else -> "Revisar precio ahora"
                    }
                )
            }
        }
    }
}

@Composable
private fun PriceChangeRow(change: PriceChange) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(change.tienda, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        val difference = change.difference
        val (text, color) = when {
            change.now == null -> "ya no disponible" to MaterialTheme.colorScheme.onSurfaceVariant
            change.before == null -> "nuevo: ${formatSoles(change.now)}" to MaterialTheme.colorScheme.onSurface
            difference != null && difference < -0.005 ->
                "${formatSoles(change.now)} (bajó ${formatSoles(-difference)})" to MaterialTheme.colorScheme.primary
            difference != null && difference > 0.005 ->
                "${formatSoles(change.now)} (subió ${formatSoles(difference)})" to MaterialTheme.colorScheme.error
            else -> "${formatSoles(change.now)} (igual)" to MaterialTheme.colorScheme.onSurface
        }
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}
