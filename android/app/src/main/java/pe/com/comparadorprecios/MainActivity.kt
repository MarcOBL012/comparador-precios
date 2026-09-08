package pe.com.comparadorprecios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import com.clerk.api.Clerk
import com.clerk.ui.auth.AuthView
import kotlinx.coroutines.launch
import pe.com.comparadorprecios.data.OnboardingPreferences
import pe.com.comparadorprecios.data.RetrofitProvider
import pe.com.comparadorprecios.data.ScanRepository
import pe.com.comparadorprecios.ui.AppFlowState
import pe.com.comparadorprecios.ui.ErrorScreen
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
            MaterialTheme {
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
                    val scope = rememberCoroutineScope()

                    // Captura en un val local estable para que el smart-cast de más abajo
                    // no dependa de volver a invocar el getter del delegado `by`.
                    val hasSeenOnboardingSnapshot = hasSeenOnboarding
                    if (hasSeenOnboardingSnapshot == null || !clerkReady) {
                        SplashScreen()
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
                            val repository = remember {
                                ScanRepository(
                                    RetrofitProvider.create(
                                        baseUrl = BuildConfig.SCAN_BASE_URL,
                                        debug = BuildConfig.DEBUG,
                                    )
                                )
                            }
                            val vm: ScanViewModel = viewModel { ScanViewModel(repository) }
                            val state by vm.state.collectAsState()
                            var captureError by remember { mutableStateOf<String?>(null) }
                            var manualName by remember { mutableStateOf("") }

                            // `vm` es Activity-scoped (viewModel {} vive en el ViewModelStore de la
                            // Activity), así que sobrevive a salir/re-entrar de esta rama Scanning
                            // (p.ej. tras el signOut forzado por Unauthorized más abajo). Defensa
                            // adicional por si algún otro camino futuro deja Unauthorized colgado.
                            LaunchedEffect(vm) {
                                if (vm.state.value is ScanUiState.Unauthorized) vm.reset()
                            }

                            when (val s = state) {
                                is ScanUiState.Idle -> {
                                    val err = captureError
                                    if (err != null) {
                                        ErrorScreen(message = err, onRetry = { captureError = null }, onBack = { captureError = null })
                                    } else {
                                        ScanScreen(
                                            onImageCaptured = { vm.scan(it) },
                                            onError = { captureError = it },
                                        )
                                    }
                                }
                                is ScanUiState.Loading -> LoadingScreen()
                                is ScanUiState.Unauthorized -> {
                                    // Clerk.signOut() no existe; el signOut real vive en Clerk.auth
                                    // (com.clerk.api.auth.Auth#signOut), verificado vía javap.
                                    //
                                    // vm.reset() va ANTES de signOut() (que es suspend y hace red):
                                    // este composable puede ser disposed en cuanto userFlow emite
                                    // null, lo que cancelaría la corrutina a mitad de camino y
                                    // saltaría un reset colocado después. Puesto antes, el reset
                                    // corre síncronamente y siempre se aplica.
                                    //
                                    // Si signOut() falla, userFlow sigue no-nulo y esta rama
                                    // Scanning se vuelve a componer, pero ahora con un VM ya en
                                    // Idle (por el reset de arriba) en vez de quedar atascada en
                                    // Unauthorized reintentando signOut() en bucle.
                                    LaunchedEffect(Unit) {
                                        vm.reset()
                                        Clerk.auth.signOut()
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
                        }
                    }
                }
            }
        }
    }
}
