package com.kiz9r.expense_tracker

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiz9r.expense_tracker.ui.features.*
import com.kiz9r.expense_tracker.ui.theme.ExpensetrackerTheme
import com.kiz9r.expense_tracker.ingestion.scheduleIngestion
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val model: TrackerViewModel by viewModels()
    private var locked by mutableStateOf(true)
    private var lockEnabled = false
    private var authenticating = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        scheduleIngestion(this)
        setContent {
            val settings by model.settings.collectAsStateWithLifecycle()
            lockEnabled = settings.any { it.key=="app_lock" && it.value=="true" }
            val screenshots = settings.firstOrNull { it.key=="screenshots" }?.value=="true"
            SideEffect {
                if(screenshots) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            ExpensetrackerTheme {
                TrackerApp(model)
                if(lockEnabled && locked) LockDialog { authenticate() }
            }
        }
    }
    override fun onStop() { if(lockEnabled) locked=true; super.onStop() }
    private fun authenticate() {
        if(authenticating) return
        authenticating=true
        val prompt = BiometricPrompt(this,ContextCompat.getMainExecutor(this),object: BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { locked=false; authenticating=false }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { authenticating=false }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock your expense tracker")
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL).build())
    }
}
