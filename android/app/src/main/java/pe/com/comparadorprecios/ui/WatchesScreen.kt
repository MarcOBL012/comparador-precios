package pe.com.comparadorprecios.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pe.com.comparadorprecios.data.AlertView
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.Watch

/** "2,5" y "2.5" valen igual; vacío, cero o negativo no. */
fun parsePrice(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= 1_000_000 }

@Composable
fun WatchesScreen(viewModel: WatchesViewModel, snackbar: SnackbarHostState, onScan: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Watch?>(null) }
    var deleting by remember { mutableStateOf<Watch?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Seguimiento (${state.watches.size})") })
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text(if (state.unreadAlerts > 0) "Alertas · ${state.unreadAlerts} nuevas" else "Alertas") },
            )
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        when {
            state.loadError != null && state.watches.isEmpty() -> CenteredMessage(
                title = "No pudimos cargar tu seguimiento",
                body = state.loadError.orEmpty(),
                action = "Reintentar",
                onAction = viewModel::refresh,
            )
            tab == 0 -> WatchList(state.watches, state.busy, state.loading, onScan, viewModel,
                onEdit = { editing = it }, onDelete = { deleting = it })
            else -> AlertList(state.alerts, state.loading, viewModel)
        }
    }

    editing?.let { watch ->
        TargetDialog(
            watch = watch,
            onDismiss = { editing = null },
            onConfirm = { target ->
                editing = null
                if (watch.reachedTarget && target != null) viewModel.reactivate(watch, target) else viewModel.setTarget(watch, target)
            },
        )
    }
    deleting?.let { watch ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Dejar de seguir") },
            text = { Text("Se borrará el seguimiento de ${watch.producto.title} y sus alertas. El historial de precios se conserva.") },
            confirmButton = { TextButton(onClick = { deleting = null; viewModel.delete(watch) }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
    state.history?.let { HistoryDialogView(it, viewModel::closeHistory) }
}

@Composable
private fun WatchList(
    watches: List<Watch>,
    busy: Set<String>,
    loading: Boolean,
    onScan: () -> Unit,
    vm: WatchesViewModel,
    onEdit: (Watch) -> Unit,
    onDelete: (Watch) -> Unit,
) {
    if (watches.isEmpty() && !loading) {
        CenteredMessage(
            title = "Aún no sigues ningún producto",
            body = "Escanea un producto y toca \"Seguir precio\": te avisamos cuando baje a tu precio objetivo.",
            action = "Escanear un producto",
            onAction = onScan,
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(watches, key = { it.id }) { watch ->
            WatchCard(watch, watch.id in busy, vm, onEdit, onDelete)
        }
    }
}

@Composable
private fun WatchCard(watch: Watch, busy: Boolean, vm: WatchesViewModel, onEdit: (Watch) -> Unit, onDelete: (Watch) -> Unit) {
    val summary = watch.resumenPrecios
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(watch.producto.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Pill(
                    text = when {
                        watch.reachedTarget -> "Objetivo alcanzado"
                        watch.isPaused -> "En pausa"
                        else -> "Activo"
                    },
                    container = when {
                        watch.reachedTarget -> MaterialTheme.colorScheme.primaryContainer
                        watch.isPaused -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    },
                    content = when {
                        watch.reachedTarget -> MaterialTheme.colorScheme.onPrimaryContainer
                        watch.isPaused -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
            }
            if (summary.mejorPrecio != null) {
                Text(
                    "Mejor precio: ${formatSoles(summary.mejorPrecio)} en ${summary.tienda}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text("Aún sin precios. Toca revisar para buscarlos.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                buildString {
                    append(watch.precioObjetivo?.let { "Tu objetivo: ${formatSoles(it)}" } ?: "Sin objetivo: solo mínimos históricos")
                    summary.minimoHistorico?.let { append(" · Mínimo visto: ${formatSoles(it)}") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Consultando tiendas…", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { vm.check(watch.id) }, enabled = !busy && !watch.isPaused) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Revisar ahora")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.openHistory(watch) }, enabled = !busy) {
                    Icon(Icons.Filled.ShowChart, contentDescription = "Ver historial")
                }
                IconButton(
                    onClick = { if (watch.reachedTarget) onEdit(watch) else vm.togglePause(watch) },
                    enabled = !busy,
                ) {
                    Icon(
                        if (watch.isPaused || watch.reachedTarget) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (watch.isPaused) "Reactivar" else if (watch.reachedTarget) "Reactivar con otro objetivo" else "Pausar",
                    )
                }
                TextButton(onClick = { onEdit(watch) }, enabled = !busy) { Text("Objetivo") }
                IconButton(onClick = { onDelete(watch) }, enabled = !busy) {
                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun AlertList(alerts: List<AlertView>, loading: Boolean, vm: WatchesViewModel) {
    if (alerts.isEmpty() && !loading) {
        CenteredMessage(
            title = "Sin alertas por ahora",
            body = "Cuando un producto que sigues llegue a tu objetivo o a su precio más bajo, lo verás aquí.",
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        items(alerts, key = { it.id }) { alert ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (alert.leida) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(alert.tipo.nombre, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(alert.mensaje, style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = { vm.markAlert(alert, !alert.leida) }) { Text(if (alert.leida) "No leída" else "Leída") }
                }
            }
        }
    }
}

@Composable
private fun TargetDialog(watch: Watch, onDismiss: () -> Unit, onConfirm: (Double?) -> Unit) {
    var text by remember { mutableStateOf(watch.precioObjetivo?.let { "%.2f".format(java.util.Locale.US, it) }.orEmpty()) }
    val parsed = parsePrice(text)
    val invalid = text.isNotBlank() && parsed == null
    val mustSetNew = watch.reachedTarget
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mustSetNew) "Reactivar seguimiento" else "Precio objetivo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (mustSetNew) "Ya llegó a tu objetivo. Para seguir, fija uno nuevo más bajo que el precio actual."
                    else "Te avisamos cuando el mejor precio llegue a este valor. Déjalo vacío para recibir solo mínimos históricos.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Precio objetivo (S/)") },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) ({ Text("Escribe un monto mayor a 0, ej. 2.50") }) else null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(parsed) }, enabled = !invalid && (!mustSetNew || parsed != null)) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun HistoryDialogView(dialog: HistoryDialog, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Historial de precios") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(dialog.watch.producto.title, fontWeight = FontWeight.Bold)
                when {
                    dialog.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Cargando…")
                    }
                    dialog.error != null -> Text(dialog.error, color = MaterialTheme.colorScheme.error)
                    dialog.history == null || dialog.history.estadisticas.cantidad == 0 ->
                        Text("Todavía no hay precios guardados. Toca \"Revisar ahora\".")
                    else -> {
                        val s = dialog.history.estadisticas
                        Text("Últimos ${dialog.history.dias} días · ${s.cantidad} precios")
                        Text("Mínimo ${formatSoles(s.minimo ?: 0.0)} · Promedio ${formatSoles(s.promedio ?: 0.0)} · Máximo ${formatSoles(s.maximo ?: 0.0)}")
                        Text(
                            when (s.tendencia) {
                                "baja" -> "📉 Tendencia a la baja"
                                "sube" -> "📈 Tendencia al alza"
                                "estable" -> "➖ Precio estable"
                                else -> "Aún sin tendencia (hace falta más de un día de datos)"
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                        dialog.history.puntos.takeLast(8).reversed().forEach { p ->
                            Text("${p.observadoEn.take(10)} · ${p.tienda}: ${formatSoles(p.precio)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun CenteredMessage(title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

/**
 * En el resultado de un escaneo: seguir el producto con un objetivo sugerido 10 % bajo el mejor
 * precio (un ancla concreta le ahorra a la persona decidir un número desde cero).
 */
@Composable
fun FollowPriceCard(identification: Identification, bestPrice: Double?, viewModel: WatchesViewModel) {
    val follow by viewModel.follow.collectAsState()
    val suggested = remember(bestPrice) { bestPrice?.let { "%.2f".format(java.util.Locale.US, it * 0.9) }.orEmpty() }
    var text by remember(suggested) { mutableStateOf(suggested) }
    LaunchedEffect(identification) { viewModel.resetFollow() }
    val parsed = parsePrice(text)
    val invalid = text.isNotBlank() && parsed == null

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Avísame cuando baje", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            when (val f = follow) {
                is FollowUiState.Done -> Text(
                    "✅ Ya sigues este producto. Revisa tus alertas en la pestaña Seguimiento.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Precio objetivo (S/)") },
                        singleLine = true,
                        isError = invalid,
                        supportingText = { Text(if (invalid) "Escribe un monto mayor a 0, ej. 2.50" else "Opcional: vacío = solo avisarte de mínimos") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (f is FollowUiState.Failed) {
                        Text(f.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { viewModel.follow(identification, parsed) },
                        enabled = !invalid && f != FollowUiState.Saving,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (f == FollowUiState.Saving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Seguir precio", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
