package pe.com.comparadorprecios.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

object AppRoutes {
    const val SCAN = "scan"
    const val HISTORY = "history"
    const val SHOPPING = "shopping"
    const val DETAIL_ARG = "scanId"
    const val DETAIL = "detail/{$DETAIL_ARG}"
    const val PROFILE = "profile"
    fun detailRoute(id: Long): String = "detail/$id"

    /** Pantallas de "profundidad": sin barras, con su propio botón de volver. */
    val FULL_SCREEN = setOf(DETAIL, PROFILE)
}

private data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

private val BOTTOM_DESTINATIONS = listOf(
    BottomDestination(AppRoutes.SCAN, "Escanear", Icons.Filled.PhotoCamera),
    BottomDestination(AppRoutes.HISTORY, "Historial", Icons.Filled.History),
    BottomDestination(AppRoutes.SHOPPING, "Lista", Icons.Filled.ShoppingCart),
)

/** Perfil y cierre de sesión; cerrar sesión pide confirmación para evitar un toque accidental. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(navController: NavController, onSignOut: () -> Unit) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: AppRoutes.SCAN
    if (current in AppRoutes.FULL_SCREEN) return
    var confirmSignOut by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text("Comparador de precios") },
        actions = {
            IconButton(onClick = { navController.navigate(AppRoutes.PROFILE) { launchSingleTop = true } }) {
                Icon(Icons.Filled.AccountCircle, contentDescription = "Mi perfil")
            }
            IconButton(onClick = { confirmSignOut = true }) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Cerrar sesión")
            }
        },
    )
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Cerrar sesión") },
            text = { Text("Tendrás que iniciar sesión de nuevo para volver a escanear.") },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text("Cerrar sesión") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
fun AppBottomBar(navController: NavController) {
    val backStack by navController.currentBackStackEntryAsState()
    // En el detalle y el perfil se oculta la barra para dar aire al contenido.
    val current = backStack?.destination?.route ?: AppRoutes.SCAN
    if (current in AppRoutes.FULL_SCREEN) return
    NavigationBar {
        BOTTOM_DESTINATIONS.forEach { dest ->
            NavigationBarItem(
                selected = current == dest.route,
                onClick = {
                    navController.navigate(dest.route) {
                        popUpTo(AppRoutes.SCAN) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(dest.icon, contentDescription = dest.label) },
                label = { Text(dest.label) },
            )
        }
    }
}
