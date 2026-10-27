package pe.com.comparadorprecios.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.flowOf
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.InsightsMode
import pe.com.comparadorprecios.data.InsightsModeStore
import pe.com.comparadorprecios.data.NearbyStore
import pe.com.comparadorprecios.data.ProductInsights
import pe.com.comparadorprecios.data.ScanRepository
import pe.com.comparadorprecios.data.StoreResult
import pe.com.comparadorprecios.location.UserLocationProvider
import pe.com.comparadorprecios.location.hasLocationPermission
import pe.com.comparadorprecios.profile.ProfileStore
import pe.com.comparadorprecios.util.StoreLocations

/** Lo que necesitan las tarjetas extra; se arma una vez en MainActivity. */
class ProductExtrasDeps(
    val repository: ScanRepository,
    val modeStore: InsightsModeStore,
    val locationProvider: UserLocationProvider,
    val profileStore: ProfileStore,
    /** Cuenta activa (Clerk); null = sin perfil. */
    val userId: String?,
)

/** Un ViewModel por resultado (key): el puntaje, los consejos y el mapa comparten la misma instancia. */
@Composable
fun rememberProductExtras(
    key: String,
    identification: Identification,
    tiendas: List<StoreResult>,
    deps: ProductExtrasDeps,
): ProductExtrasViewModel {
    val chains = remember(tiendas) { StoreLocations.chainsToLocate(tiendas) }
    return viewModel(key = "$key-${deps.userId}") {
        ProductExtrasViewModel(
            identification = identification,
            chains = chains,
            repository = deps.repository,
            modeStore = deps.modeStore,
            locationProvider = deps.locationProvider,
            profileInterests = deps.userId?.let { deps.profileStore.interests(it) } ?: flowOf(emptySet()),
        )
    }
}

// ─────────────────────────────── Puntaje (estilo Yuka) ───────────────────────────────

private data class ScoreStyle(val color: Color, val label: String)

/** Escala y colores de Yuka/Nutri-Score: la gente ya los asocia a "bueno/malo" sin leer. */
private fun scoreStyle(score: Int): ScoreStyle = when {
    score >= 75 -> ScoreStyle(Color(0xFF038141), "Excelente")
    score >= 50 -> ScoreStyle(Color(0xFF85BB2F), "Bueno")
    score >= 25 -> ScoreStyle(Color(0xFFEE8100), "Regular")
    else -> ScoreStyle(Color(0xFFE63E11), "Bajo")
}

/**
 * Anillo con el puntaje junto al nombre del producto: lo primero que ve el ojo. Se llena con
 * animación al cargar — un pequeño momento de recompensa que invita a seguir leyendo abajo.
 */
@Composable
fun ProductScoreBadge(viewModel: ProductExtrasViewModel) {
    val state by viewModel.insights.collectAsState()
    when (val s = state) {
        InsightsUiState.Loading -> ScoreRing(score = null, size = 68.dp)
        is InsightsUiState.Ready -> s.insights.puntaje?.let { ScoreRing(score = it.toInt().coerceIn(0, 100), size = 68.dp) }
        InsightsUiState.Failed -> Unit
    }
}

@Composable
private fun ScoreRing(score: Int?, size: Dp) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val style = score?.let(::scoreStyle)
    val progress = remember(score) { Animatable(0f) }
    LaunchedEffect(score) {
        if (score != null) progress.animateTo(score / 100f, tween(900, easing = FastOutSlowInEasing))
    }
    val pulse by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "ring-pulse",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().alpha(if (score == null) pulse else 1f)) {
                val stroke = 7.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                if (style != null) {
                    drawArc(
                        style.color, -90f, 360f * progress.value, false, Offset(inset, inset), arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            if (score != null && style != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$score", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = style.color)
                    Text("/100", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        }
        if (style != null) {
            Text(style.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = style.color)
        }
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
 * Consejos y datos del producto según el modo y el perfil. Va justo después del mejor precio:
 * lo personal ("Para ti") arriba de todo, porque lo que habla de uno mismo es lo que más se lee.
 */
@Composable
fun ProductInsightsCard(viewModel: ProductExtrasViewModel, onOpenProfile: (() -> Unit)?) {
    val selected by viewModel.selectedMode.collectAsState()
    val state by viewModel.insights.collectAsState()
    val profile by viewModel.profile.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Descubre más",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                if (onOpenProfile != null) {
                    TextButton(onClick = onOpenProfile) {
                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (profile.isEmpty()) "Mi perfil" else "Mi perfil · ${profile.size}")
                    }
                }
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 18.dp),
            ) {
                items(MODE_OPTIONS, key = { it.id }) { option ->
                    FilterChip(
                        selected = option.id == selected,
                        onClick = { viewModel.selectMode(option.id) },
                        label = { Text(option.label, fontWeight = FontWeight.Medium) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                            selectedLabelColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
            }
            Box(modifier = Modifier.padding(horizontal = 18.dp)) {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) },
                    label = "insights",
                ) { s ->
                    when (s) {
                        InsightsUiState.Loading -> InsightsSkeleton()
                        is InsightsUiState.Ready -> InsightsContent(
                            modo = s.modo,
                            insights = s.insights,
                            autoChosen = selected == InsightsMode.AUTO,
                            profile = profile,
                            onOpenProfile = onOpenProfile,
                        )
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
private fun InsightsContent(
    modo: String,
    insights: ProductInsights,
    autoChosen: Boolean,
    profile: Set<String>,
    onOpenProfile: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when {
            insights.paraTi.isNotBlank() -> ForYouBanner(insights.paraTi, profile)
            profile.isEmpty() && onOpenProfile != null -> PersonalizeBanner(onOpenProfile)
        }
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
        Text(insights.titular, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Column {
            insights.puntos.take(3).forEachIndexed { index, punto ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                InsightRow(punto.emoji, punto.texto)
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

/** "Para ti": el bloque más llamativo, con el perfil a la vista para que se note que es personal. */
@Composable
private fun ForYouBanner(text: String, profile: Set<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0B7A4B), Color(0xFF1E9E6A), Color(0xFFD9480F))))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("✨ PARA TI", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
        if (profile.isNotEmpty()) {
            Text(
                "Según tu perfil: ${profile.take(3).joinToString(" · ")}${if (profile.size > 3) " +${profile.size - 3}" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

/** Sin perfil todavía: la invitación a armarlo aparece donde estaría lo personalizado. */
@Composable
private fun PersonalizeBanner(onOpenProfile: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onOpenProfile)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🎯", fontSize = 28.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Personaliza tus consejos",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                "Cuéntanos tus metas (bajar azúcar, gaming…) y te diremos lo que importa para ti.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text("→", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** Fila estilo Yuka: ícono en un círculo de color y texto al lado. */
@Composable
private fun InsightRow(emoji: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 20.sp)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
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
        modifier = Modifier.fillMaxWidth().background(container, RoundedCornerShape(18.dp)).padding(14.dp),
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
        SkeletonLine(1f, 64.dp)
        SkeletonLine(0.85f, 26.dp)
        repeat(3) { SkeletonLine(0.95f, 18.dp) }
        MutedText("Preparando datos para ti…")
    }
}

@Composable
private fun SkeletonLine(widthFraction: Float, height: Dp) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
    )
}

// ─────────────────────────────── Tienda más cercana + mapa ───────────────────────────────

/**
 * "Llévatelo hoy": mapa con tu posición, las tiendas cercanas y la ruta a la elegida. Explica para
 * qué es la ubicación ANTES de pedir el permiso — pedirlo en frío baja la aceptación.
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
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Llévatelo hoy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            }
            when (val s = state) {
                NearbyUiState.Idle -> {
                    MutedText("Te mostramos en el mapa la tienda más cercana donde lo venden y cómo llegar. Usamos tu ubicación solo para esto y no la guardamos.")
                    Button(onClick = requestOrFind, modifier = Modifier.fillMaxWidth().height(52.dp)) {
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
                is NearbyUiState.Ready -> NearbyMapContent(
                    state = s,
                    onSelectStore = viewModel::selectStore,
                    onNavigate = { store ->
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(StoreLocations.directionsUrl(store.lat, store.lng)))
                        )
                    },
                )
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

@Composable
private fun NearbyMapContent(
    state: NearbyUiState.Ready,
    onSelectStore: (Int) -> Unit,
    onNavigate: (NearbyStore) -> Unit,
) {
    val store = state.selectedStore
    val route = (state.route as? RouteUiState.Ready)?.route
    val routeColor = if (route?.walking == false) Color(0xFF1A73E8) else MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RouteSummary(store, state.route, routeColor)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            StoreMap(
                origin = state.origin,
                stores = state.tiendas,
                selected = state.selected,
                route = route,
                routeColor = routeColor,
                onSelectStore = onSelectStore,
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                "© OpenStreetMap",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF333333),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (state.tiendas.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(state.tiendas, key = { _, t -> t.cadena }) { index, t ->
                    FilterChip(
                        selected = index == state.selected,
                        onClick = { onSelectStore(index) },
                        label = { Text("${t.cadena} · ${StoreLocations.formatDistance(t.distanciaKm)}") },
                        shape = RoundedCornerShape(50),
                    )
                }
            }
        }
        Button(
            onClick = { onNavigate(store) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = routeColor, contentColor = Color.White),
        ) {
            Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Iniciar navegación", fontWeight = FontWeight.Bold)
        }
        Text(
            "Disponible online en ${store.cadena}. El stock puede variar por tienda.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Como el resumen de Google Maps: el tiempo grande primero, porque es lo que decide si vas o no. */
@Composable
private fun RouteSummary(store: NearbyStore, route: RouteUiState, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when (route) {
            is RouteUiState.Ready -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (route.route.walking) Icons.AutoMirrored.Filled.DirectionsWalk else Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("${route.route.minutos} min", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = color)
                Spacer(Modifier.width(8.dp))
                Text(
                    "(${StoreLocations.formatDistance(route.route.distanciaKm)}${if (route.route.walking) " caminando" else " en auto"})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RouteUiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                MutedText("Trazando la ruta…")
            }
            RouteUiState.Unavailable -> Text(
                "A ${StoreLocations.formatDistance(store.distanciaKm)} en línea recta",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(store.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (store.direccion.isNotBlank()) {
            Text(store.direccion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
