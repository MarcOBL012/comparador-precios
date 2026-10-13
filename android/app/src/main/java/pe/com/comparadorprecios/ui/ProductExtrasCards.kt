package pe.com.comparadorprecios.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyListScope
import androidx.lifecycle.viewmodel.compose.viewModel
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.InsightsMode
import pe.com.comparadorprecios.data.InsightsModeStore
import pe.com.comparadorprecios.data.NearbyStore
import pe.com.comparadorprecios.data.ProductInsights
import pe.com.comparadorprecios.data.ScanRepository
import pe.com.comparadorprecios.data.StoreResult
import pe.com.comparadorprecios.location.UserLocationProvider
import pe.com.comparadorprecios.location.hasLocationPermission
import pe.com.comparadorprecios.util.StoreLocations

/**
 * Agrega "Llévatelo hoy" y "Descubre más de tu producto" a una lista de resultados. La tarjeta
 * de tienda solo aparece si el producto se encontró en una cadena con tiendas físicas.
 */
fun LazyListScope.productExtras(
    key: String,
    identification: Identification,
    tiendas: List<StoreResult>,
    repository: ScanRepository,
    insightsPreferences: InsightsModeStore,
    locationProvider: UserLocationProvider,
) {
    val chains = StoreLocations.chainsToLocate(tiendas)
    val create = { ProductExtrasViewModel(identification, chains, repository, insightsPreferences, locationProvider) }
    // Misma key en ambos items → ambos reciben la misma instancia del ViewModel.
    if (chains.isNotEmpty()) {
        item(key = "$key-nearby") { NearbyStoreCard(viewModel(key = key) { create() }) }
    }
    item(key = "$key-insights") { ProductInsightsCard(viewModel(key = key) { create() }) }
}

// ─────────────────────────────── Tienda más cercana ───────────────────────────────

/**
 * "Llévatelo hoy": la tienda física más cercana de las cadenas donde se encontró el producto.
 * Explica para qué es la ubicación ANTES de pedir el permiso — pedirlo en frío baja la aceptación.
 */
@Composable
fun NearbyStoreCard(viewModel: ProductExtrasViewModel) {
    if (!viewModel.hasChainsToLocate) return
    val context = LocalContext.current
    val state by viewModel.nearby.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) viewModel.findNearbyStores() else viewModel.onLocationPermissionDenied()
    }
    val requestOrFind = {
        if (hasLocationPermission(context)) {
            viewModel.findNearbyStores()
        } else {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    // Si el permiso ya estaba dado, no hay fricción que justificar: se busca solo.
    LaunchedEffect(Unit) {
        if (state == NearbyUiState.Idle && hasLocationPermission(context)) viewModel.findNearbyStores()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Llévatelo hoy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "nearby",
            ) { s ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (s) {
                        NearbyUiState.Idle -> {
                            MutedText("Te mostramos la tienda más cercana donde lo venden. Usamos tu ubicación solo para esto y no la guardamos.")
                            Button(onClick = requestOrFind, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.NearMe, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Ver tienda más cercana", fontWeight = FontWeight.Bold)
                            }
                        }
                        NearbyUiState.Locating -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            MutedText("Buscando la tienda más cercana a ti…")
                        }
                        is NearbyUiState.Ready -> NearbyStoresContent(s.tiendas) { store ->
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(StoreLocations.directionsUrl(store.lat, store.lng)))
                            )
                        }
                        NearbyUiState.NoStoresNearby -> MutedText("No encontramos tiendas de estas cadenas cerca de ti. Puedes comprarlo online desde los precios de arriba.")
                        NearbyUiState.LocationUnavailable -> RetryRow(
                            "Activa la ubicación del celular para encontrar la tienda más cercana.",
                            onRetry = requestOrFind,
                        )
                        NearbyUiState.PermissionDenied -> RetryRow(
                            "Sin tu ubicación no podemos calcular qué tienda te queda más cerca.",
                            onRetry = requestOrFind,
                            retryLabel = "Permitir ubicación",
                        )
                        NearbyUiState.Failed -> RetryRow("No pudimos buscar tiendas ahora.", onRetry = requestOrFind)
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyStoresContent(tiendas: List<NearbyStore>, onDirections: (NearbyStore) -> Unit) {
    val nearest = tiendas.first()
    val walking = StoreLocations.walkingMinutes(nearest.distanciaKm)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                StoreLocations.formatDistance(nearest.distanciaKm),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (walking != null) {
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp).padding(bottom = 4.dp),
                )
                Text(
                    "≈ $walking min",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        Text(nearest.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        if (nearest.direccion.isNotBlank()) {
            Text(nearest.direccion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Button(
            onClick = { onDirections(nearest) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Cómo llegar", fontWeight = FontWeight.Bold)
        }
        tiendas.drop(1).forEach { other ->
            Text(
                "También: ${other.cadena} a ${StoreLocations.formatDistance(other.distanciaKm)} →",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.clickable { onDirections(other) }.padding(vertical = 4.dp),
            )
        }
        Text(
            "Disponible online en ${nearest.cadena}. El stock puede variar por tienda.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
    }
}

// ─────────────────────────────── Datos del producto ───────────────────────────────

private data class ModeOption(val id: String, val label: String)

private val MODE_OPTIONS = listOf(
    ModeOption(InsightsMode.AUTO, "✨ Automático"),
    ModeOption(InsightsMode.SALUD, "🍏 Salud"),
    ModeOption(InsightsMode.TECNOLOGIA, "💡 Tecnología"),
    ModeOption(InsightsMode.CURIOSIDADES, "🔍 Curiosidades"),
)

private fun modeName(mode: String): String = when (mode) {
    InsightsMode.SALUD -> "Salud"
    InsightsMode.TECNOLOGIA -> "Tecnología"
    else -> "Curiosidades"
}

/**
 * Consejos y datos del producto según el modo (Salud, Tecnología, Curiosidades). El modo se
 * recuerda entre escaneos: en "modo salud" todo lo que escanees viene con su semáforo.
 */
@Composable
fun ProductInsightsCard(viewModel: ProductExtrasViewModel) {
    val selected by viewModel.selectedMode.collectAsState()
    val state by viewModel.insights.collectAsState()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Descubre más de tu producto",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            ) {
                items(MODE_OPTIONS, key = { it.id }) { option ->
                    FilterChip(
                        selected = option.id == selected,
                        onClick = { viewModel.selectMode(option.id) },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                            selectedLabelColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                    )
                }
            }
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) },
                    label = "insights",
                ) { s ->
                    when (s) {
                        InsightsUiState.Loading -> InsightsSkeleton()
                        is InsightsUiState.Ready -> InsightsContent(s.modo, s.insights, autoChosen = selected == InsightsMode.AUTO)
                        InsightsUiState.Failed -> RetryRow(
                            "No pudimos cargar los datos del producto.",
                            onRetry = viewModel::retryInsights,
                            icon = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightsContent(modo: String, insights: ProductInsights, autoChosen: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (insights.semaforo in setOf("verde", "ambar", "rojo")) {
                HealthSignal(insights.semaforo, insights.etiquetaSemaforo)
            } else if (insights.etiquetaSemaforo.isNotBlank()) {
                Pill(
                    text = insights.etiquetaSemaforo,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (autoChosen) {
                Text(
                    "Modo ${modeName(modo)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(insights.titular, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        insights.puntos.take(3).forEach { punto ->
            Row(verticalAlignment = Alignment.Top) {
                Text(punto.emoji, fontSize = 20.sp, modifier = Modifier.width(32.dp))
                Text(punto.texto, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (insights.consejo.isNotBlank()) {
            Highlight(
                label = "👉 Tu consejo de hoy",
                text = insights.consejo,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        if (insights.datoCurioso.isNotBlank()) {
            Highlight(
                label = "💡 ¿Sabías que…?",
                text = insights.datoCurioso,
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (modo == InsightsMode.SALUD) {
            Text(
                "Información general generada con IA. No reemplaza la opinión de un profesional de salud.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Verde/ámbar/rojo: se entiende en medio segundo, antes de leer una sola palabra. */
@Composable
private fun HealthSignal(semaforo: String, label: String) {
    // Colores fijos (no del tema): el semáforo debe leerse igual en modo claro y oscuro.
    val (dot, container) = when (semaforo) {
        "verde" -> Color(0xFF0B7A4B) to Color(0xFFCDEFD9)
        "ambar" -> Color(0xFFE8A200) to Color(0xFFFFF0C2)
        else -> Color(0xFFBA1A1A) to Color(0xFFFFDAD6)
    }
    Row(
        modifier = Modifier.background(container, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(dot, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            label.ifBlank { if (semaforo == "verde") "Buena opción" else if (semaforo == "ambar") "Con moderación" else "Ocasional" },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B1B1B),
        )
    }
}

@Composable
private fun Highlight(label: String, text: String, container: Color, content: Color) {
    Column(
        modifier = Modifier.fillMaxWidth().background(container, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = content)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = content)
    }
}

/** Esqueleto que "respira" mientras la IA responde: la espera se siente más corta que un spinner. */
@Composable
private fun InsightsSkeleton() {
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.alpha(alpha)) {
        SkeletonLine(0.4f, 22.dp)
        SkeletonLine(0.85f, 24.dp)
        repeat(3) { SkeletonLine(0.95f, 16.dp) }
        MutedText("Preparando datos para ti…")
    }
}

@Composable
private fun SkeletonLine(widthFraction: Float, height: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
    )
}

// ─────────────────────────────── Comunes ───────────────────────────────

@Composable
private fun MutedText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RetryRow(
    message: String,
    onRetry: () -> Unit,
    retryLabel: String = "Reintentar",
    icon: ImageVector? = Icons.Filled.LocationOff,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
            }
            MutedText(message)
        }
        OutlinedButton(onClick = onRetry) { Text(retryLabel) }
    }
}
