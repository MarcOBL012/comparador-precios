package pe.com.comparadorprecios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import pe.com.comparadorprecios.ui.ComparadorTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.room.Room
import pe.com.comparadorprecios.data.RetrofitProvider
import pe.com.comparadorprecios.data.ScanRepository
import pe.com.comparadorprecios.history.AppDatabase
import pe.com.comparadorprecios.history.HistoryRepository
import pe.com.comparadorprecios.ui.AppBottomBar
import pe.com.comparadorprecios.ui.AppRoutes
import pe.com.comparadorprecios.ui.DetailScreen
import pe.com.comparadorprecios.ui.DetailViewModel
import pe.com.comparadorprecios.ui.ErrorScreen
import pe.com.comparadorprecios.ui.HistoryScreen
import pe.com.comparadorprecios.ui.HistoryViewModel
import pe.com.comparadorprecios.ui.LoadingScreen
import pe.com.comparadorprecios.ui.LowConfidenceScreen
import pe.com.comparadorprecios.ui.ResultScreen
import pe.com.comparadorprecios.ui.ScanScreen
import pe.com.comparadorprecios.ui.ScanUiState
import pe.com.comparadorprecios.ui.ScanViewModel
import pe.com.comparadorprecios.ui.openWebSearch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComparadorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val context = LocalContext.current
                    val repository = remember {
                        ScanRepository(
                            RetrofitProvider.create(
                                baseUrl = BuildConfig.SCAN_BASE_URL,
                                debug = BuildConfig.DEBUG,
                            )
                        )
                    }
                    val historyRepository = remember {
                        val db = Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "comparador-precios.db",
                        ).build()
                        HistoryRepository(db.historyDao())
                    }
                    val vm: ScanViewModel = viewModel { ScanViewModel(repository) }
                    val navController = rememberNavController()
                    val snackbar = remember { SnackbarHostState() }
                    Scaffold(
                        bottomBar = { AppBottomBar(navController) },
                        snackbarHost = { SnackbarHost(snackbar) },
                    ) { padding ->
                        NavHost(
                            navController = navController,
                            startDestination = AppRoutes.SCAN,
                            modifier = Modifier.padding(padding),
                        ) {
                            composable(AppRoutes.SCAN) {
                                val state by vm.state.collectAsState()
                                var captureError by remember { mutableStateOf<String?>(null) }
                                var manualName by remember { mutableStateOf("") }
                                var pendingThumbnail by remember { mutableStateOf<ByteArray?>(null) }

                                when (val s = state) {
                                    is ScanUiState.Idle -> {
                                        val err = captureError
                                        if (err != null) {
                                            ErrorScreen(message = err, onRetry = { captureError = null }, onBack = { captureError = null })
                                        } else {
                                            ScanScreen(
                                                onImageCaptured = { uri, thumbnail ->
                                                    pendingThumbnail = thumbnail
                                                    vm.scan(uri)
                                                },
                                                onError = { captureError = it },
                                            )
                                        }
                                    }
                                    is ScanUiState.Loading -> LoadingScreen()
                                    is ScanUiState.LowConfidence -> LowConfidenceScreen(
                                        identification = s.identification,
                                        manualName = manualName,
                                        onManualNameChange = { manualName = it },
                                        onRetake = { vm.reset() },
                                        onSearchWeb = { base, q -> openWebSearch(context, base, q) },
                                    )
                                    is ScanUiState.Success -> ResultScreen(
                                        identification = s.identification,
                                        tiendas = s.tiendas,
                                        onNewScan = { vm.reset() },
                                    )
                                    is ScanUiState.Error -> ErrorScreen(
                                        message = s.message,
                                        onRetry = { vm.reset() },
                                        onBack = { vm.reset() },
                                    )
                                }
                                LaunchedEffect(state) {
                                    val s = state
                                    if (s is ScanUiState.Success) {
                                        val thumbnail = pendingThumbnail
                                        pendingThumbnail = null
                                        if (thumbnail != null) {
                                            runCatching {
                                                historyRepository.save(s.identification, s.tiendas, thumbnail)
                                            }
                                        }
                                    }
                                }
                            }
                            composable(AppRoutes.HISTORY) {
                                val historyVm: HistoryViewModel = viewModel { HistoryViewModel(historyRepository) }
                                HistoryScreen(
                                    viewModel = historyVm,
                                    snackbar = snackbar,
                                    onOpen = { id -> navController.navigate(AppRoutes.detailRoute(id)) },
                                )
                            }
                            composable(
                                route = AppRoutes.DETAIL,
                                arguments = listOf(navArgument(AppRoutes.DETAIL_ARG) { type = NavType.LongType }),
                            ) { backStackEntry ->
                                val scanId = backStackEntry.arguments?.getLong(AppRoutes.DETAIL_ARG) ?: return@composable
                                val detailVm: DetailViewModel = viewModel { DetailViewModel(historyRepository, scanId) }
                                val item by detailVm.item.collectAsState()
                                val loaded = item
                                if (loaded != null) {
                                    DetailScreen(item = loaded, onBack = { navController.popBackStack() })
                                } else {
                                    LoadingScreen()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
