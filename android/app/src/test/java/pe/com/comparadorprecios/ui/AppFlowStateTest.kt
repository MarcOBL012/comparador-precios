package pe.com.comparadorprecios.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppFlowStateTest {

    @Test
    fun `onboarding no vista manda a Onboarding sin importar la sesion`() {
        assertEquals(AppFlowState.Onboarding, AppFlowState.from(hasSeenOnboarding = false, isSignedIn = false))
        assertEquals(AppFlowState.Onboarding, AppFlowState.from(hasSeenOnboarding = false, isSignedIn = true))
    }

    @Test
    fun `onboarding vista y sin sesion manda a Auth`() {
        assertEquals(AppFlowState.Auth, AppFlowState.from(hasSeenOnboarding = true, isSignedIn = false))
    }

    @Test
    fun `onboarding vista y con sesion manda a Scanning`() {
        assertEquals(AppFlowState.Scanning, AppFlowState.from(hasSeenOnboarding = true, isSignedIn = true))
    }
}
