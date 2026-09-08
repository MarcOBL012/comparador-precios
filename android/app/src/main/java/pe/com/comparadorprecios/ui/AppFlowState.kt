package pe.com.comparadorprecios.ui

sealed interface AppFlowState {
    data object Onboarding : AppFlowState
    data object Auth : AppFlowState
    data object Scanning : AppFlowState

    companion object {
        fun from(hasSeenOnboarding: Boolean, isSignedIn: Boolean): AppFlowState = when {
            !hasSeenOnboarding -> Onboarding
            !isSignedIn -> Auth
            else -> Scanning
        }
    }
}
