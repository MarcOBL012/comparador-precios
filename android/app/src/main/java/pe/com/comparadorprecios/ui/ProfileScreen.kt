package pe.com.comparadorprecios.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.com.comparadorprecios.data.InsightsMode
import pe.com.comparadorprecios.data.InsightsModeStore
import pe.com.comparadorprecios.profile.ProfileCatalog
import pe.com.comparadorprecios.profile.ProfileStore

class ProfileViewModel(
    private val userId: String,
    private val store: ProfileStore,
    private val modeStore: InsightsModeStore,
) : ViewModel() {
    val interests: StateFlow<Set<String>> =
        store.interests(userId).stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val mode: StateFlow<String> =
        modeStore.mode.stateIn(viewModelScope, SharingStarted.Eagerly, InsightsMode.AUTO)

    /** Se lee lo guardado (no el StateFlow) para que dos toques rápidos no se pisen. */
    fun toggle(label: String) {
        viewModelScope.launch {
            val current = store.interests(userId).first()
            store.setInterests(userId, if (label in current) current - label else current + label)
        }
    }

    fun setMode(mode: String) {
        viewModelScope.launch { modeStore.setMode(mode) }
    }
}

private val PROFILE_MODES = listOf(
    InsightsMode.AUTO to "✨ Automático",
    InsightsMode.SALUD to "🍏 Salud",
    InsightsMode.TECNOLOGIA to "💡 Tecnología",
    InsightsMode.CURIOSIDADES to "🔍 Curiosidades",
)

/** Con 3 intereses ya hay consejos bien personalizados; la barra invita a llegar ahí. */
private const val GOOD_PROFILE_SIZE = 3

/**
 * "Mi perfil": metas de salud, uso de tecnología y temas de interés. Se guarda al instante (sin
 * botón "Guardar") y queda ligado a la cuenta en este celular.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    displayName: String?,
    onBack: () -> Unit,
) {
    val interests by viewModel.interests.collectAsState()
    val mode by viewModel.mode.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "hero") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF0B7A4B), Color(0xFF14945C))))
                    .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
                Row(modifier = Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(56.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            displayName?.firstOrNull()?.uppercase() ?: "🙂",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Tu perfil", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text(
                            displayName ?: "Personaliza tus consejos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
                Spacer(Modifier.padding(top = 16.dp))
                Column(modifier = Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        when {
                            interests.isEmpty() -> "Elige lo que te importa y cada producto te dirá algo para ti."
                            interests.size < GOOD_PROFILE_SIZE -> "¡Vas bien! Elige ${GOOD_PROFILE_SIZE - interests.size} más para consejos más precisos."
                            else -> "Perfil listo: ${interests.size} intereses. Tus consejos ya son personalizados ✨"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                    LinearProgressIndicator(
                        progress = { (interests.size.toFloat() / GOOD_PROFILE_SIZE).coerceAtMost(1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
            }
        }

        item(key = "mode") {
            ProfileSection(
                title = "Modo preferido",
                subtitle = "Qué tipo de datos ves primero en cada producto",
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PROFILE_MODES.forEach { (id, label) ->
                        FilterChip(
                            selected = mode == id,
                            onClick = { viewModel.setMode(id) },
                            label = { Text(label) },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                                selectedLabelColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                    }
                }
            }
        }

        ProfileCatalog.GROUPS.forEach { group ->
            item(key = "group-${group.mode}") {
                ProfileSection(title = group.title, subtitle = group.subtitle) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.interests.forEach { interest ->
                            FilterChip(
                                selected = interest.label in interests,
                                onClick = { viewModel.toggle(interest.label) },
                                label = { Text("${interest.emoji} ${interest.label}") },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }
                }
            }
        }

        item(key = "privacy") {
            Text(
                "🔒 Tu perfil se guarda solo en este celular, ligado a tu cuenta. Solo se usa para personalizar los consejos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun ProfileSection(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
