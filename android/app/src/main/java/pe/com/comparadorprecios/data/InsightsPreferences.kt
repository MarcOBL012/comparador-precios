package pe.com.comparadorprecios.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.insightsDataStore: DataStore<Preferences> by preferencesDataStore(name = "insights")

private val MODE = stringPreferencesKey("mode")

/** El modo que eligió la persona (Salud, Tecnología…) se recuerda entre escaneos. */
interface InsightsModeStore {
    val mode: Flow<String>
    suspend fun setMode(mode: String)
}

class InsightsPreferences(private val context: Context) : InsightsModeStore {
    override val mode: Flow<String> =
        context.insightsDataStore.data.map { it[MODE] ?: InsightsMode.AUTO }

    override suspend fun setMode(mode: String) {
        context.insightsDataStore.edit { it[MODE] = mode }
    }
}
