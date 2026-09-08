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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.room.Room
import com.clerk.api.Clerk
import com.clerk.ui.auth.AuthView
import kotlinx.coroutines.launch
import pe.com.comparadorprecios.data.OnboardingPreferences
import pe.com.comparadorprecios.data.RetrofitProvider
import pe.com.comparadorprecios.data.ScanRepository
import pe.com.comparadorprecios.history.AppDatabase
import pe.com.comparadorprecios.history.HistoryRepository
import pe.com.comparadorprecios.ui.AppBottomBar
import pe.com.comparadorprecios.ui.AppFlowState
import pe.com.comparadorprecios.ui.AppRoutes
import pe.com.comparadorprecios.ui.DetailScreen
import pe.com.comparadorprecios.ui.DetailViewModel
import pe.com.comparadorprecios.ui.ErrorScreen
import pe.com.comparadorprecios.ui.HistoryScreen
import pe.com.comparadorprecios.ui.HistoryViewModel
import pe.com.comparadorprecios.ui.LoadingScreen
import pe.com.comparadorprecios.ui.LowConfidenceScreen
import pe.com.comparadorprecios.ui.OnboardingScreen
import pe.com.comparadorprecios.ui.ResultScreen
import pe.com.comparadorprecios.ui.ScanScreen
import pe.com.comparadorprecios.ui.ScanUiState
import pe.com.comparadorprecios.ui.ScanViewModel
import pe.com.comparadorprecios.ui.SigningOutScreen
import pe.com.comparadorprecios.ui.SplashScreen
import pe.com.comparadorprecios.ui.openWebSearch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComparadorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val context = LocalContext.current

                    val onboardingPrefs = remember { OnboardingPreferences(context) }
                    // initial = null: distingue "DataStore no ha hecho su primera lectura todavía"
                    // de "false, no ha visto el onboarding" (evita el flicker de cold-start).
                    val hasSeenOnboarding by onboardingPrefs.hasSeenOnboarding.collectAsState(initial = null)
                    val user by Clerk.userFlow.collectAsStateWithLifecycle()
                    // Clerk.userFlow arranca en null antes de que Clerk termine su init async
                    // (con llamada de red incluida); isInitialized distingue ese estado transitorio
                    // de "de verdad no hay sesión", evitando que un usuario ya logueado vea
                    // brevemente (u offline, indefinidamente) la pantalla de sign-in.
                    val clerkReady by Clerk.isInitialized.collectAsStateWithLifecycle()
                    // Clerk.initializationError (verificado vía javap: StateFlow<Throwable?>,
                    // respaldado por ConfigurationManager.getInitializationError()) queda no-null
                    // en cualquier intento de init fallido sin cliente/entorno en caché — la SDK
                    // ya reintenta sola con backoff (5s/10s/20s) y su monitor de conectividad puede
                    // reintentar más tarde por su cuenta, así que este error puede aparecer y
                    // desaparecer solo; mostrarlo (con un botón que llama a Clerk.reinitialize()
                    // como empujón manual) evita que la app se quede como spinner mudo mientras
                    // tanto, en vez de ser la única vía de recuperación.
                    val clerkInitError by Clerk.initializationError.collectAsStateWithLifecycle()
                    val scope = rememberCoroutineScope()

                    // Captura en un val local estable para que el smart-cast de más abajo
                    // no dependa de volver a invocar el getter del delegado `by`.
                    val hasSeenOnboardingSnapshot = hasSeenOnboarding

                    // Onboarding no necesita red ni sesión de Clerk: se muestra apenas DataStore
                    // confirma que el usuario no lo ha visto, sin esperar a que Clerk termine de
                    // inicializar (antes quedaba bloqueado detrás de clerkReady también).
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
                        // Inalcanzable en la práctica: al llegar aquí hasSeenOnboardingSnapshot ya
                        // es `true` (el `false` y el `null` retornan antes, arriba), así que
                        // AppFlowState.from() nunca devuelve Onboarding en este punto. Se mantiene
                        // la rama por exhaustividad del `when` sellado y como red de seguridad.
                        AppFlowState.Onboarding -> OnboardingScreen(
                            onContinue = { scope.launch { onboardingPrefs.markOnboardingSeen() } },
                        )
                        AppFlowState.Auth -> AuthView()
                        AppFlowState.Scanning -> {
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
                                            is ScanUiState.Unauthorized -> {
                                                // Clerk.signOut() no existe; el signOut real vive en Clerk.auth
                                                // (com.clerk.api.auth.Auth#signOut), verificado vía javap.
                                                //
                                                // La secuencia signOut()→reset() vive en el ViewModel
                                                // (viewModelScope, Activity-scoped) en vez de en este
                                                // LaunchedEffect (composable-scoped): si el reset ocurriera
                                                // síncronamente ANTES de signOut() (como en un intento previo),
                                                // el propio cambio de estado a Idle dispara la recomposición que
                                                // saca de esta rama Unauthorized, lo que hace dispose de este
                                                // LaunchedEffect y cancela signOut() en su primer punto de
                                                // suspensión — dejando al usuario sin cerrar sesión realmente
                                                // (ni local ni en el servidor de Clerk). Con
                                                // signOutAfterUnauthorized(), signOut() corre en
                                                // viewModelScope, que sobrevive a la recomposición/dispose de
                                                // este composable, y el reset a Idle solo ocurre en el
                                                // `finally` una vez signOut() termina (éxito o falla).
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
    }
}
