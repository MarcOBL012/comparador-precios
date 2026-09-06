package pe.com.comparadorprecios.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class OnboardingFeature(val titulo: String, val descripcion: String)

private val ONBOARDING_FEATURES = listOf(
    OnboardingFeature(
        "Escanea un producto",
        "Toma una foto del empaque y la identificamos automáticamente.",
    ),
    OnboardingFeature(
        "Compara precios",
        "Buscamos el mismo producto en Plaza Vea y Wong en tiempo real.",
    ),
    OnboardingFeature(
        "Elige dónde comprar",
        "Ves el precio y el nombre exacto encontrado en cada tienda, para confirmar que es el mismo producto.",
    ),
)

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            "Comparador de precios",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Encuentra el mejor precio para lo que compras, en segundos.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        ONBOARDING_FEATURES.forEach { feature ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(feature.titulo, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(feature.descripcion, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Empezar")
        }
    }
}
