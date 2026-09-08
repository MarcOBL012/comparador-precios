package pe.com.comparadorprecios.ui

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.StoreResult
import java.text.NumberFormat
import java.util.Locale

private val soles = NumberFormat.getCurrencyInstance(Locale("es", "PE"))

@Composable
fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Identificando el producto y comparando precios…")
        Spacer(Modifier.height(8.dp))
        Text(
            "Esto puede tardar varios segundos.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Splash genérico para el arranque en frío: se muestra mientras Clerk
 * termina su inicialización async y/o DataStore no ha leído aún si el
 * usuario ya vio el onboarding (evita el flicker Onboarding→Auth→Scanning).
 */
@Composable
fun SplashScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
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
        Text("Ocurrió un problema", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(message)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack) { Text("Tomar otra foto") }
    }
}

/**
 * Comparación — LazyColumn genérica sobre `tiendas` (NO hardcodear 2 tiendas;
 * un futuro Plan 2b puede agregar más sin actualizar la app).
 *
 * Mitigación obligatoria del bug de matching: se muestra `producto`
 * (nombre completo matcheado) junto al precio, no solo el precio.
 */
@Composable
fun ResultScreen(
    identification: Identification,
    tiendas: List<StoreResult>,
    onNewScan: () -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "${identification.marca} ${identification.nombre} ${identification.presentacion}".trim(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "${identification.categoria} · confianza ${(identification.confianza * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        if (tiendas.isEmpty()) {
            Text("Sin resultados en tiendas.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tiendas, key = { it.tienda }) { tienda ->
                    StoreRow(
                        result = tienda,
                        onOpenUrl = { url ->
                            CustomTabsIntent.Builder().build()
                                .launchUrl(context, Uri.parse(url))
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onNewScan, modifier = Modifier.fillMaxWidth()) {
            Text("Escanear otro producto")
        }
    }
}

@Composable
private fun StoreRow(result: StoreResult, onOpenUrl: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(result.tienda, fontWeight = FontWeight.Bold)
                when (result.estado) {
                    "encontrado" -> Text(
                        soles.format(result.precio ?: 0.0),
                        fontWeight = FontWeight.Bold,
                    )
                    "no_encontrado" -> Text("no disponible")
                    else -> Text("error")
                }
            }
            // Nombre completo del match — mitigación del bug de multipacks (HANDOFF).
            if (result.estado == "encontrado") {
                Spacer(Modifier.height(4.dp))
                Text(result.producto.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                val url = result.url
                if (!url.isNullOrBlank()) {
                    ClickableText(
                        text = AnnotatedString("Ver en tienda"),
                        onClick = { onOpenUrl(url) },
                    )
                }
            } else if (result.estado == "error") {
                Spacer(Modifier.height(4.dp))
                Text(
                    result.mensaje ?: "No se pudo consultar esta tienda.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * Baja confianza (tiendas: []) — reintentar foto o ingreso manual.
 * MVP honesto: el backend solo acepta imagen, así que el nombre manual abre
 * la búsqueda web de cada tienda en el navegador (sin inventar endpoint).
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
        Text("No pudimos identificarlo bien", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("La foto salió borrosa o el empaque no se ve claro (confianza ${(identification.confianza * 100).toInt()}%). Toma otra foto o busca por nombre:")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetake, modifier = Modifier.fillMaxWidth()) {
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
