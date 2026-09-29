package pe.com.comparadorprecios.ui

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.StoreResult
import pe.com.comparadorprecios.data.title
import pe.com.comparadorprecios.util.PriceInsights
import pe.com.comparadorprecios.util.PriceSummary
import java.text.NumberFormat
import java.util.Locale

private val soles = NumberFormat.getCurrencyInstance(Locale("es", "PE"))

fun formatSoles(value: Double): String = soles.format(value)

private val LOADING_STEPS = listOf(
    "Leyendo el empaque…",
    "Identificando marca y presentación…",
    "Buscando en supermercados y tiendas…",
    "Comparando precios para ti…",
)

/** Contar qué se está haciendo hace que la espera se sienta más corta que un spinner mudo. */
@Composable
fun LoadingScreen(steps: List<String> = LOADING_STEPS) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(steps) {
        while (step < steps.lastIndex) {
            delay(1_800)
            step++
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(96.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Savings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        AnimatedContent(targetState = steps[step], label = "loading-step") { text ->
            Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
        Spacer(Modifier.height(8.dp))
        Text(
            "Suele tardar unos segundos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Splash genérico para el arranque en frío: se muestra mientras Clerk
 * termina su inicialización async y/o DataStore no ha leído aún si el
 * usuario ya vio el onboarding (evita el flicker Onboarding→Auth→Scanning).
 *
 * Si Clerk.initializationError trae un error se muestra un mensaje y un
 * botón de reintento en vez de un spinner mudo indefinido. La SDK ya
 * reintenta sola con backoff; el botón (Clerk.reinitialize()) es un empujón
 * manual adicional, no la única vía de recuperación.
 */
@Composable
fun SplashScreen(
    initializationError: Throwable? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (initializationError != null) {
            Text(
                "No se pudo conectar. Revisa tu internet.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (onRetry != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text("Reintentar") }
            }
        } else {
            CircularProgressIndicator()
        }
    }
}

/** Se muestra brevemente mientras se cierra la sesión tras un 401 (ver ScanUiState.Unauthorized). */
@Composable
fun SigningOutScreen(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(message)
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(80.dp).background(MaterialTheme.colorScheme.errorContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.SearchOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Ocurrió un problema", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Tomar otra foto") }
    }
}

/**
 * Comparación — lista genérica sobre `tiendas` (no asume cuántas hay).
 *
 * Mitigación obligatoria del bug de matching: se muestra `producto`
 * (nombre completo matcheado) junto al precio, no solo el precio.
 */
@Composable
fun ResultScreen(
    identification: Identification,
    tiendas: List<StoreResult>,
    onNewScan: () -> Unit,
    newScanLabel: String = "Escanear otro producto",
    webSearchSkipped: Boolean = false,
    onAddToList: (() -> Unit)? = null,
    extraContent: LazyListScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val summary = remember(tiendas) { PriceInsights.summarize(tiendas) }
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { ProductHeader(identification) }
            if (webSearchSkipped) {
                item(key = "battery-notice") {
                    ContextNotices(listOf("Batería baja: no se buscó en otras tiendas de internet. Carga el celular para ver más opciones."))
                }
            }
            if (summary != null) {
                item(key = "best-price") { BestPriceHero(summary) }
            } else {
                item(key = "no-offers") { NoOffersCard(hasStores = tiendas.isNotEmpty()) }
            }
            itemsIndexed(tiendas, key = { index, tienda -> "$index-${tienda.tienda}" }) { _, tienda ->
                StoreRow(
                    result = tienda,
                    summary = summary,
                    onOpenUrl = { url ->
                        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                    },
                )
            }
            extraContent()
        }
        Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onAddToList != null && summary != null) {
                    Button(
                        onClick = onAddToList,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                    ) {
                        Icon(Icons.Filled.AddShoppingCart, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Agregar a mi lista", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(onClick = onNewScan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(newScanLabel)
                }
            }
        }
    }
}

/**
 * ADAPTACIÓN: pide confirmar si el resultado era el producto correcto. "Sí" lo marca como
 * confirmado (referencia para la próxima vez que se escanee lo mismo, ver
 * [ProductConfirmationViewModel.previousConfirmedMatch]); "No" pide un nombre completo o link y
 * reintenta la búsqueda con eso. Autocontenido: solo depende del ViewModel, no del estado de
 * ResultScreen, para poder insertarse vía `extraContent` sin tocar su firma.
 */
@Composable
fun ProductConfirmationCard(viewModel: ProductConfirmationViewModel) {
    val state by viewModel.state.collectAsState()
    val previousMatch by viewModel.previousConfirmedMatch.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val match = previousMatch
        if (match != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Ya confirmaste este producto antes" +
                            (match.bestPrice?.let { " — ${formatSoles(it)} en un escaneo previo" } ?: "") +
                            ". Puede estar desactualizado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (val s = state) {
                    ConfirmationState.Asking -> {
                        Text(
                            "¿Es este el producto que buscabas?",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = viewModel::confirmCorrect, modifier = Modifier.weight(1f)) {
                                Text("Sí, es este")
                            }
                            OutlinedButton(onClick = viewModel::showCorrectionForm, modifier = Modifier.weight(1f)) {
                                Text("No es este")
                            }
                        }
                    }
                    ConfirmationState.Confirmed -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("¡Gracias! Guardamos que este resultado es correcto.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    ConfirmationState.CorrectionForm -> {
                        var nombre by remember { mutableStateOf("") }
                        var link by remember { mutableStateOf("") }
                        Text("Ayúdanos a encontrarlo", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = nombre,
                            onValueChange = { nombre = it },
                            label = { Text("Nombre completo del producto") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = link,
                            onValueChange = { link = it },
                            label = { Text("Link directo (opcional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.submitCorrection(nombre, link) },
                                enabled = nombre.isNotBlank() || link.isNotBlank(),
                                modifier = Modifier.weight(1f),
                            ) { Text("Buscar de nuevo") }
                            OutlinedButton(onClick = viewModel::cancelCorrection, modifier = Modifier.weight(1f)) {
                                Text("Cancelar")
                            }
                        }
                    }
                    ConfirmationState.SearchingCorrection -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Buscando de nuevo con ese nombre…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    is ConfirmationState.CorrectionResult -> {
                        Text("Guardamos tu corrección.", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        val encontrados = s.tiendas.filter { it.isFound }
                        if (encontrados.isNotEmpty()) {
                            Text("Encontramos esto con el nombre que diste:", style = MaterialTheme.typography.bodySmall)
                            encontrados.forEach { tienda ->
                                Text(
                                    "• ${tienda.tienda}: ${tienda.precio?.let(::formatSoles) ?: "—"} ${tienda.producto.orEmpty()}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        } else {
                            Text(
                                "Tampoco encontramos precios con ese nombre, pero guardamos tu corrección para revisarla más adelante.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    is ConfirmationState.CorrectionFailed -> {
                        Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = viewModel::showCorrectionForm) { Text("Reintentar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductHeader(identification: Identification) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val category = PRODUCT_CATEGORIES.firstOrNull { it.id == identification.categoria }
            Pill(
                text = category?.label ?: identification.categoria,
                icon = category?.icon,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (identification.tipo.isNotBlank()) {
                Pill(
                    text = identification.tipo,
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(identification.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Identificado con ${(identification.confianza * 100).toInt()}% de confianza",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** El dato que la persona vino a buscar, arriba y grande; el ahorro como ancla frente a la opción más cara. */
@Composable
private fun BestPriceHero(summary: PriceSummary) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(primary, primary.copy(alpha = 0.78f))),
                RoundedCornerShape(24.dp),
            )
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "MEJOR PRECIO",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                fontWeight = FontWeight.Bold,
            )
            Text(
                formatSoles(summary.bestPrice),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "en ${summary.bestStore}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            if (summary.offersCount > 1 && summary.savings > 0.005) {
                Spacer(Modifier.height(8.dp))
                Pill(
                    text = "Ahorras ${formatSoles(summary.savings)} frente a la más cara",
                    icon = Icons.Filled.Savings,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            } else if (summary.offersCount == 1) {
                Text(
                    "Es la única tienda que lo tiene ahora",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun NoOffersCard(hasStores: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    if (hasStores) "Ninguna tienda lo tiene ahora" else "Sin resultados en tiendas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Prueba con otra foto donde se lea bien la marca, o elige la categoría antes de escanear.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StoreRow(result: StoreResult, summary: PriceSummary?, onOpenUrl: (String) -> Unit) {
    val isBest = summary != null && result.isFound && result.tienda == summary.bestStore && result.precio == summary.bestPrice
    val extra = PriceInsights.extraOverBest(result, summary)
    val url = result.url?.takeIf { result.isFound && it.isNotBlank() }
    Card(
        onClick = { url?.let(onOpenUrl) },
        enabled = url != null,
        modifier = Modifier.fillMaxWidth().alpha(if (result.isFound) 1f else 0.6f),
        colors = CardDefaults.cardColors(
            containerColor = if (isBest) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isBest) 0.dp else 1.dp),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            StoreAvatar(result.tienda, highlighted = isBest)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(result.tienda, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                when (result.estado) {
                    "encontrado" -> {
                        Text(
                            result.producto.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (result.isFromWeb) {
                            Text(
                                "vía Google Shopping",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (result.isFromWebSearchFallback) {
                            Text(
                                "Enlace encontrado, sin precio confirmado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    "no_encontrado" -> Text("No disponible", style = MaterialTheme.typography.bodySmall)
                    else -> Text(
                        result.mensaje ?: "No se pudo consultar esta tienda.",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (result.isFound && (result.precio != null || url != null)) {
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    if (result.precio != null) {
                        Text(
                            formatSoles(result.precio),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isBest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        when {
                            isBest -> Pill(
                                text = "MEJOR",
                                container = MaterialTheme.colorScheme.primary,
                                content = MaterialTheme.colorScheme.onPrimary,
                            )
                            extra != null -> Text(
                                "+${formatSoles(extra)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (url != null) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Ver en tienda",
                            modifier = Modifier.size(16.dp).padding(top = 2.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreAvatar(name: String, highlighted: Boolean) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull()?.uppercase() ?: "?",
            fontWeight = FontWeight.Bold,
            color = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
fun Pill(
    text: String,
    container: Color,
    content: Color,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier.background(container, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = content, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

/** Avisos de adaptación al contexto. `urgent` = bloquea algo (sin red); si no, es informativo. */
@Composable
fun ContextNotices(notices: List<String>, modifier: Modifier = Modifier, urgent: Boolean = false) {
    if (notices.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (urgent) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (urgent) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            notices.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/**
 * Baja confianza (tiendas: []) — reintentar foto o ingreso manual.
 * El nombre manual abre la búsqueda web de cada tienda en el navegador.
 */
@Composable
fun LowConfidenceScreen(
    identification: Identification,
    manualName: String,
    onManualNameChange: (String) -> Unit,
    onRetake: () -> Unit,
    onSearchWeb: (storeBaseUrl: String, query: String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("No pudimos identificarlo bien", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("La foto salió borrosa o el empaque no se ve claro (confianza ${(identification.confianza * 100).toInt()}%). Toma otra foto o busca por nombre:")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetake, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Tomar otra foto")
        }
        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.OutlinedTextField(
            value = manualName,
            onValueChange = onManualNameChange,
            label = { Text("Nombre del producto") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onSearchWeb("https://www.plazavea.com.pe/search?text=", manualName) },
                enabled = manualName.isNotBlank(),
            ) { Text("Plaza Vea") }
            OutlinedButton(
                onClick = { onSearchWeb("https://www.wong.pe/search?text=", manualName) },
                enabled = manualName.isNotBlank(),
            ) { Text("Wong") }
        }
    }
}
