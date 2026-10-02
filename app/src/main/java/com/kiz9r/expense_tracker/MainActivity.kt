package com.kiz9r.expense_tracker

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiz9r.expense_tracker.ui.features.*
import com.kiz9r.expense_tracker.ui.theme.ExpensetrackerTheme
import com.kiz9r.expense_tracker.ingestion.scheduleIngestion
import com.kiz9r.expense_tracker.security.ScreenshotDebugPreference
import com.kiz9r.expense_tracker.security.shouldProtectWindow
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @javax.inject.Inject lateinit var recovery: com.kiz9r.expense_tracker.security.LedgerRecovery
    private var startup by mutableStateOf("loading")
    private val model: TrackerViewModel by viewModels()
    private var locked by mutableStateOf(true)
    private var lockEnabled = false
    private var authenticating = false
    private var displaySettingsReady by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        checkLedger()
        setContent {
            if(startup!="ready") {
                ExpensetrackerTheme {
                    if(startup=="loading") androidx.compose.material3.Surface(modifier=androidx.compose.ui.Modifier.fillMaxSize()) { Screen("startup") {Heading("SBI / PERSONAL","Opening your encrypted ledger…"); androidx.compose.material3.LinearProgressIndicator()} }
                    else RecoveryScreen(recovery,{checkLedger()}) { finishAffinity(); android.os.Process.killProcess(android.os.Process.myPid()) }
                }
                return@setContent
            }
            val ready by model.ready.collectAsStateWithLifecycle()
            LaunchedEffect(ready) {
                if(ready) {
                    try {
                        if(ScreenshotDebugPreference(this@MainActivity).enableOnce(model.ledger))
                            model.message.value="Screenshots are enabled for debugging. You can turn them off in Settings."
                        displaySettingsReady=true
                    } catch(e: kotlinx.coroutines.CancellationException) {throw e}
                    catch(_: Exception) {model.message.value="Could not enable screenshots automatically. Enable them in Settings."}
                }
            }
            val settings by model.settings.collectAsStateWithLifecycle()
            lockEnabled = settings.any { it.key=="app_lock" && it.value=="true" }
            val screenshots = settings.firstOrNull { it.key=="screenshots" }?.value=="true"
            val themeMode=settings.firstOrNull {it.key=="theme_mode"}?.value ?: "dark"
            val light=themeMode=="light" || (themeMode=="system" && !androidx.compose.foundation.isSystemInDarkTheme())
            val protectWindow=shouldProtectWindow(displaySettingsReady,screenshots,lockEnabled && locked)
            SideEffect {
                androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).apply {
                    isAppearanceLightStatusBars=light;isAppearanceLightNavigationBars=light
                }
                if(protectWindow) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            ExpensetrackerTheme(mode=settings.firstOrNull { it.key=="theme_mode" }?.value ?: "dark") {
                TrackerApp(model)
                if(lockEnabled && locked) LockDialog { authenticate() }
            }
        }
    }
    private fun checkLedger() {
        startup="loading"
        displaySettingsReady=false
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        lifecycleScope.launch {
            try { recovery.checkAccessible(); startup="ready"; scheduleIngestion(this@MainActivity) }
            catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(_: Exception) { startup="recovery" }
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
