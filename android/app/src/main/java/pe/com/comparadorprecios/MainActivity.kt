package pe.com.comparadorprecios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.clerk.api.Clerk
import com.clerk.ui.auth.AuthView
import kotlinx.coroutines.launch
import pe.com.comparadorprecios.context.ContextPolicy
import pe.com.comparadorprecios.context.DeviceContextMonitor
import pe.com.comparadorprecios.data.OnboardingPreferences
import pe.com.comparadorprecios.data.RetrofitProvider
import pe.com.comparadorprecios.data.ScanRepository
import pe.com.comparadorprecios.history.AppDatabase
import pe.com.comparadorprecios.history.HistoryRepository
import pe.com.comparadorprecios.pending.PendingScanProcessor
import pe.com.comparadorprecios.pending.PendingScanRepository
import pe.com.comparadorprecios.shopping.ShoppingListRepository
import pe.com.comparadorprecios.ui.AppBottomBar
import pe.com.comparadorprecios.ui.AppFlowState
import pe.com.comparadorprecios.ui.AppRoutes
import pe.com.comparadorprecios.ui.AppTopBar
import pe.com.comparadorprecios.ui.ComparadorTheme
import pe.com.comparadorprecios.ui.DetailScreen
import pe.com.comparadorprecios.ui.DetailViewModel
import pe.com.comparadorprecios.ui.ErrorScreen
import pe.com.comparadorprecios.ui.HistoryScreen
import pe.com.comparadorprecios.ui.HistoryViewModel
import pe.com.comparadorprecios.ui.LoadingScreen
import pe.com.comparadorprecios.ui.LowConfidenceScreen
import pe.com.comparadorprecios.ui.OnboardingScreen
import pe.com.comparadorprecios.ui.PendingScansViewModel
import pe.com.comparadorprecios.ui.ProductConfirmationCard
import pe.com.comparadorprecios.ui.ProductConfirmationViewModel
import pe.com.comparadorprecios.ui.ResultScreen
import pe.com.comparadorprecios.ui.ScanScreen
import pe.com.comparadorprecios.ui.ScanUiState
import pe.com.comparadorprecios.ui.ScanViewModel
import pe.com.comparadorprecios.ui.ShoppingListScreen
import pe.com.comparadorprecios.ui.ShoppingListViewModel
import pe.com.comparadorprecios.ui.SigningOutScreen
import pe.com.comparadorprecios.ui.SplashScreen
import pe.com.comparadorprecios.ui.openWebSearch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComparadorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val context = LocalContext.current

                    val onboardingPrefs = remember { OnboardingPreferences(context) }
                    val hasSeenOnboarding by onboardingPrefs.hasSeenOnboarding.collectAsState(initial = null)
                    val user by Clerk.userFlow.collectAsStateWithLifecycle()
                    val clerkReady by Clerk.isInitialized.collectAsStateWithLifecycle()
                    val clerkInitError by Clerk.initializationError.collectAsStateWithLifecycle()
                    val scope = rememberCoroutineScope()

                    val hasSeenOnboardingSnapshot = hasSeenOnboarding

                    if (hasSeenOnboardingSnapshot == false) {
                        OnboardingScreen(
                            onContinue = { scope.launch { onboardingPrefs.markOnboardingSeen() } },
                        )
                        return@Surface
                    }

                    if (hasSeenOnboardingSnapshot == null || !clerkReady) {
                        SplashScreen(
                            initializationError = clerkInitError,
                            onRetry = { Clerk.reinitialize() },
                        )
                        return@Surface
                    }

                    val flowState = AppFlowState.from(
                        hasSeenOnboarding = hasSeenOnboardingSnapshot,
                        isSignedIn = user != null,
                    )

                    when (flowState) {
                        AppFlowState.Onboarding -> OnboardingScreen(
                            onContinue = { scope.launch { onboardingPrefs.markOnboardingSeen() } },
                        )
                        AppFlowState.Auth -> AuthView()
                        AppFlowState.Scanning -> {
                            val contextMonitor = remember { DeviceContextMonitor(context) }
                            // remember: sin él cada recomposición registraría otro callback de red y batería.
                            val deviceContextFlow = remember { contextMonitor.observe() }
                            val deviceContext by deviceContextFlow.collectAsStateWithLifecycle(initialValue = contextMonitor.current())
                            val policy = remember(deviceContext) { ContextPolicy.decide(deviceContext) }

                            val repository = remember {
                                ScanRepository(
                                    RetrofitProvider.create(
                                        baseUrl = BuildConfig.SCAN_BASE_URL,
                                        debug = BuildConfig.DEBUG,
                                    )
                                )
                            }
                            val db = remember { AppDatabase.build(context) }
                            val historyRepository = remember { HistoryRepository(db.historyDao()) }
                            val shoppingListRepository = remember { ShoppingListRepository(db.shoppingDao()) }
                            val pendingRepository = remember {
                                PendingScanRepository(db.pendingScanDao(), File(context.filesDir, "pending-scans"))
                            }
                            val pendingProcessor = remember {
                                PendingScanProcessor(pendingRepository, repository, historyRepository)
                            }

                            val vm: ScanViewModel = viewModel { ScanViewModel(repository) }
                            val pendingVm: PendingScansViewModel =
                                viewModel { PendingScansViewModel(pendingRepository, pendingProcessor) }
                            val pendingScans by pendingVm.pending.collectAsState()
                            val processingPending by pendingVm.processing.collectAsState()
                            val pendingMessage by pendingVm.message.collectAsState()
                            var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
                            val navController = rememberNavController()
                            val snackbar = remember { SnackbarHostState() }

                            // Vuelve la conexión (o se abre la app con pendientes de una sesión
                            // anterior) → se procesa la cola sola, sin que el usuario haga nada.
                            LaunchedEffect(policy.offline) {
                                if (!policy.offline) pendingVm.processPending(policy.saveBattery)
                            }

                            LaunchedEffect(pendingMessage) {
                                pendingMessage?.let {
                                    snackbar.showSnackbar(it)
                                    pendingVm.dismissMessage()
                                }
                            }

                            Scaffold(
                                topBar = {
                                    AppTopBar(
                                        navController = navController,
                                        onSignOut = { scope.launch { Clerk.auth.signOut() } },
                                    )
                                },
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
                                        var lastSavedScanId by remember { mutableStateOf<Long?>(null) }

                                        when (val s = state) {
                                            is ScanUiState.Idle -> {
                                                val err = captureError
                                                if (err != null) {
                                                    ErrorScreen(message = err, onRetry = { captureError = null }, onBack = { captureError = null })
                                                } else {
                                                    ScanScreen(
                                                        policy = policy,
                                                        selectedCategory = selectedCategory,
                                                        onCategoryChange = { selectedCategory = it },
                                                        pendingCount = pendingScans.size,
                                                        onImageCaptured = { uri, thumbnail ->
                                                            // Sin conexión no se llama ni a Gemini ni a las
                                                            // tiendas: la foto se encola tal cual.
                                                            if (policy.offline) {
                                                                pendingVm.saveForLater(uri, thumbnail, selectedCategory)
                                                            } else {
                                                                pendingThumbnail = thumbnail
                                                                vm.scan(uri, saveBattery = policy.saveBattery, categoria = selectedCategory)
                                                            }
                                                        },
                                                        onError = { captureError = it },
                                                    )
                                                }
                                            }
                                            is ScanUiState.Loading -> LoadingScreen()
                                            is ScanUiState.Unauthorized -> {
                                                LaunchedEffect(Unit) {
                                                    vm.signOutAfterUnauthorized { Clerk.auth.signOut() }
                                                }
                                                SigningOutScreen(message = s.message)
                                            }
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
                                                webSearchSkipped = s.webSearchSkipped,
                                                onAddToList = {
                                                    scope.launch {
                                                        shoppingListRepository.add(s.identification, s.tiendas)
                                                        snackbar.showSnackbar("Producto agregado a tu lista de compras")
                                                    }
                                                },
                                                extraContent = {
                                                    val scanId = lastSavedScanId
                                                    if (scanId != null) {
                                                        item(key = "confirmation") {
                                                            val confirmVm: ProductConfirmationViewModel = viewModel(key = "confirm-$scanId") {
                                                                ProductConfirmationViewModel(
                                                                    scanId = scanId,
                                                                    identification = s.identification,
                                                                    saveBattery = policy.saveBattery,
                                                                    historyRepository = historyRepository,
                                                                    scanRepository = repository,
                                                                )
                                                            }
                                                            ProductConfirmationCard(confirmVm)
                                                        }
                                                    }
                                                },
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
                                                    }.onSuccess { id -> lastSavedScanId = id }
                                                }
                                            } else if (s is ScanUiState.Idle) {
                                                // Escaneo nuevo: que la tarjeta de confirmación no siga
                                                // apuntando al scanId del producto anterior.
                                                lastSavedScanId = null
                                            }
                                        }
                                    }
                                    composable(AppRoutes.HISTORY) {
                                        val historyVm: HistoryViewModel = viewModel { HistoryViewModel(historyRepository) }
                                        HistoryScreen(
                                            viewModel = historyVm,
                                            snackbar = snackbar,
                                            onOpen = { id -> navController.navigate(AppRoutes.detailRoute(id)) },
                                            pendingScans = pendingScans,
                                            processingPending = processingPending,
                                            offline = policy.offline,
                                            onProcessPending = {
                                                pendingVm.processPending(policy.saveBattery, announceEmpty = true)
                                            },
                                            onDiscardPending = { pendingVm.discard(it) },
                                        )
                                    }
                                    composable(AppRoutes.SHOPPING) {
                                        val shoppingVm: ShoppingListViewModel = viewModel { ShoppingListViewModel(shoppingListRepository, repository) }
                                        ShoppingListScreen(
                                            viewModel = shoppingVm,
                                            snackbar = snackbar,
                                            canRefresh = !policy.offline,
                                            saveBattery = policy.saveBattery,
                                            onGoScan = {
                                                navController.navigate(AppRoutes.SCAN) {
                                                    popUpTo(AppRoutes.SCAN) { inclusive = false }
                                                    launchSingleTop = true
                                                }
                                            },
                                        )
                                    }
                                    composable(
                                        route = AppRoutes.DETAIL,
                                        arguments = listOf(navArgument(AppRoutes.DETAIL_ARG) { type = NavType.LongType }),
                                    ) { backStackEntry ->
                                        val scanId = backStackEntry.arguments?.getLong(AppRoutes.DETAIL_ARG) ?: return@composable
                                        val detailVm: DetailViewModel = viewModel { DetailViewModel(historyRepository, repository, scanId) }
                                        val item by detailVm.item.collectAsState()
                                        val latestCheck by detailVm.latestCheck.collectAsState()
                                        val checking by detailVm.checking.collectAsState()
                                        val checkError by detailVm.checkError.collectAsState()
                                        val loaded = item
                                        if (loaded != null) {
                                            DetailScreen(
                                                item = loaded,
                                                latestCheck = latestCheck,
                                                checking = checking,
                                                checkError = checkError,
                                                canCheck = !policy.offline,
                                                onCheckPrices = { detailVm.checkPrices(policy.saveBattery) },
                                                onAddToList = { tiendas ->
                                                    scope.launch {
                                                        shoppingListRepository.add(loaded.identification, tiendas)
                                                        snackbar.showSnackbar("Producto agregado a tu lista de compras")
                                                    }
                                                },
                                                onBack = { navController.popBackStack() },
                                            )
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
    }
}
