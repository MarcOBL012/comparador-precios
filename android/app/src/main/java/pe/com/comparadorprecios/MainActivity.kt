package pe.com.comparadorprecios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import pe.com.comparadorprecios.ui.openWebSearch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val context = LocalContext.current
                    val onboardingPrefs = remember { OnboardingPreferences(context) }
                    val hasSeenOnboarding by onboardingPrefs.hasSeenOnboarding.collectAsState(initial = false)
                    val user by Clerk.userFlow.collectAsStateWithLifecycle()
                    val scope = rememberCoroutineScope()

                    val flowState = AppFlowState.from(
                        hasSeenOnboarding = hasSeenOnboarding,
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
