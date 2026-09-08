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
