package pe.com.comparadorprecios.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

object AppRoutes {
    const val SCAN = "scan"
    const val HISTORY = "history"
    const val DETAIL_ARG = "scanId"
    const val DETAIL = "detail/{$DETAIL_ARG}"
    fun detailRoute(id: Long): String = "detail/$id"
}

private data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

private val BOTTOM_DESTINATIONS = listOf(
    BottomDestination(AppRoutes.SCAN, "Escanear", Icons.Filled.PhotoCamera),
    BottomDestination(AppRoutes.HISTORY, "Historial", Icons.Filled.History),
)

@Composable
fun AppBottomBar(navController: NavController) {
    val backStack by navController.currentBackStackEntryAsState()
    // En el detalle se oculta la barra para dar aire a la comparación.
    val current = backStack?.destination?.route ?: AppRoutes.SCAN
    if (current == AppRoutes.DETAIL) return
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
