package pe.com.comparadorprecios.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.com.comparadorprecios.data.InsightsMode

/** Un interés del perfil. `label` es lo que se ve y también lo que se manda al prompt. */
data class Interest(val emoji: String, val label: String)

data class InterestGroup(val mode: String, val title: String, val subtitle: String, val interests: List<Interest>)

/** Catálogo cerrado: la IA solo recibe estas etiquetas, nunca texto libre del usuario. */
object ProfileCatalog {
    val GROUPS = listOf(
        InterestGroup(
            mode = InsightsMode.SALUD,
            title = "🍏 Salud",
            subtitle = "Te avisamos lo que importa según tus metas",
            interests = listOf(
                Interest("🍬", "Bajar azúcar"),
                Interest("🫀", "Cuidar la presión"),
                Interest("⚖️", "Bajar de peso"),
                Interest("💪", "Más proteína"),
                Interest("🏃", "Deportista"),
                Interest("🌱", "Vegano"),
                Interest("🥗", "Vegetariano"),
                Interest("🌾", "Sin gluten"),
                Interest("🥛", "Sin lactosa"),
                Interest("👶", "Niños en casa"),
                Interest("🤰", "Embarazo"),
            ),
        ),
        InterestGroup(
            mode = InsightsMode.TECNOLOGIA,
            title = "💡 Tecnología",
            subtitle = "Consejos según para qué lo usarías",
            interests = listOf(
                Interest("📚", "Estudio"),
                Interest("💼", "Trabajo de oficina"),
                Interest("🎮", "Gaming"),
                Interest("📷", "Fotografía"),
                Interest("🔋", "Ahorro de energía"),
                Interest("🛠️", "Que dure años"),
            ),
        ),
        InterestGroup(
            mode = InsightsMode.CURIOSIDADES,
            title = "🔍 Curiosidades",
            subtitle = "Los datos que más te interesan",
            interests = listOf(
                Interest("🏛️", "Historia"),
                Interest("🔬", "Ciencia"),
                Interest("🌎", "Origen del producto"),
                Interest("🇵🇪", "Cultura peruana"),
                Interest("♻️", "Medio ambiente"),
            ),
        ),
    )

    /**
     * Qué intereses mandar a la IA: los del modo elegido, o todos si es automático (el backend
     * decide el modo por categoría y recorta a 8).
     */
    fun interestsFor(mode: String, selected: Set<String>): List<String> {
        val groups = GROUPS.filter { it.mode == mode }.ifEmpty { GROUPS }
        return groups.flatMap { group -> group.interests.map { it.label } }.filter { it in selected }
    }
}

interface ProfileStore {
    fun interests(userId: String): Flow<Set<String>>
    suspend fun setInterests(userId: String, interests: Set<String>)
}

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "profile")

/** Un perfil por cuenta en este celular (clave = id de usuario de Clerk). No se sincroniza en la nube. */
class ProfilePreferences(private val context: Context) : ProfileStore {
    private fun key(userId: String) = stringSetPreferencesKey("interests_$userId")

    override fun interests(userId: String): Flow<Set<String>> =
        context.profileDataStore.data.map { it[key(userId)] ?: emptySet() }

    override suspend fun setInterests(userId: String, interests: Set<String>) {
        context.profileDataStore.edit { it[key(userId)] = interests }
    }
}
