package pe.com.comparadorprecios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pe.com.comparadorprecios.shopping.ShoppingLine
import pe.com.comparadorprecios.shopping.StoreBasket

@Composable
fun ShoppingListScreen(
    viewModel: ShoppingListViewModel,
    snackbar: SnackbarHostState,
    canRefresh: Boolean,
    saveBattery: Boolean,
    onGoScan: () -> Unit,
) {
    val content by viewModel.content.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val message by viewModel.message.collectAsState()

    LaunchedEffect(message) {
        val msg = message
        if (msg != null) {
            snackbar.showSnackbar(msg)
            viewModel.dismissMessage()
        }
    }

    if (content.lines.isEmpty()) {
        EmptyShoppingList(onGoScan)
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Mi lista", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                val units = content.lines.sumOf { it.quantity }
                Text(
                    "${content.lines.size} productos · $units unidades",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { viewModel.refreshPrices(saveBattery) }, enabled = canRefresh && !refreshing) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (canRefresh) "Actualizar precios" else "Sin conexión")
            }
        }
        if (refreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content.baskets.firstOrNull()?.let { best ->
                item(key = "best-basket") {
                    BestBasketHero(best = best, alternatives = content.baskets.drop(1), totalLines = content.lines.size)
                }
            }
            if (content.baskets.size > 1) {
                item(key = "other-baskets") { OtherBaskets(content.baskets.first(), content.baskets.drop(1), content.lines.size) }
            }
            item(key = "products-title") {
                Text("Productos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(content.lines, key = { it.id }) { line ->
                ShoppingLineRow(
                    line = line,
                    onIncrease = { viewModel.setQuantity(line.id, line.quantity + 1) },
                    onDecrease = { viewModel.setQuantity(line.id, line.quantity - 1) },
                    onRemove = { viewModel.remove(line.id) },
                )
            }
        }
    }
}

@Composable
private fun EmptyShoppingList(onGoScan: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(96.dp).background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.ShoppingCart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("Arma tu lista y ahorra", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Escanea productos y agrégalos: te diremos en qué tienda te sale más barata la compra completa.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onGoScan,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ),
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Escanear un producto", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BestBasketHero(best: StoreBasket, alternatives: List<StoreBasket>, totalLines: Int) {
    val primary = MaterialTheme.colorScheme.primary
    // El ahorro solo se compara contra tiendas que cubren los mismos productos; si no, no es la misma compra.
    val comparable = alternatives.filter { it.foundCount == best.foundCount }
    val savings = comparable.maxOfOrNull { it.total }?.minus(best.total)?.takeIf { it > 0.005 }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(primary, primary.copy(alpha = 0.78f))), RoundedCornerShape(24.dp))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "TE CONVIENE COMPRAR EN",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                fontWeight = FontWeight.Bold,
            )
            Text(
                best.tienda,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                formatSoles(best.total),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                if (best.foundCount == totalLines) {
                    "Tiene los $totalLines productos de tu lista"
                } else {
                    "Tiene ${best.foundCount} de $totalLines productos (falta: ${best.missing.joinToString()})"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
            )
            if (savings != null) {
                Spacer(Modifier.height(8.dp))
                Pill(
                    text = "Ahorras ${formatSoles(savings)} frente a la más cara",
                    icon = Icons.Filled.Savings,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun OtherBaskets(best: StoreBasket, others: List<StoreBasket>, totalLines: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Otras tiendas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            others.forEach { basket ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(basket.tienda, fontWeight = FontWeight.Bold)
                        Text(
                            if (basket.foundCount == totalLines) "Todos los productos" else "${basket.foundCount} de $totalLines productos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatSoles(basket.total), fontWeight = FontWeight.Bold)
                        val extra = basket.total - best.total
                        if (basket.foundCount == best.foundCount && extra > 0.005) {
                            Text(
                                "+${formatSoles(extra)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShoppingLineRow(
    line: ShoppingLine,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(line.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val bestPrice = line.tiendas.filter { it.isFound }.mapNotNull { it.precio }.minOrNull()
                Text(
                    if (bestPrice != null) "Desde ${formatSoles(bestPrice)} c/u" else "Sin precios disponibles",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (bestPrice != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = onDecrease, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = "Reducir cantidad", modifier = Modifier.size(18.dp))
            }
            Text(
                "${line.quantity}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.Center,
            )
            FilledTonalIconButton(onClick = onIncrease, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "Aumentar cantidad", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar de la lista", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
