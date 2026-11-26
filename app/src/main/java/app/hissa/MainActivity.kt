package app.hissa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        Api.codeFromLink(intent.data)?.let { Api.pendingInvite = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Api.init(applicationContext)
        Api.demo = BuildConfig.DEMO_OK && intent.getBooleanExtra("demo", false)
        Api.pendingInvite = Api.codeFromLink(intent?.data)
        setContent {
            val dark = useDark()
            // Keep the status and navigation bar icons readable when the app theme differs from the phone's.
            DisposableEffect(dark) {
                val bar = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
                onDispose { }
            }
            HissaTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // On tablets and in landscape, keep the phone layout in a centred column instead of stretching it.
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
                        androidx.compose.foundation.layout.Box(Modifier.widthIn(max = 640.dp).fillMaxHeight()) { App() }
                    }
                }
            }
        }
    }
}

@Composable
fun App() {
    val snack = remember { SnackbarHostState() }
    val c = Api.client
    var settings by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalSnack provides snack) {
        Box(Modifier.fillMaxSize()) {
            if (Api.demo) Home {}
            else if (c == null || settings) SetupScreen(canCancel = c != null) { settings = false }
            else Main(c) { settings = true }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 72.dp))
        }
    }
}

@Composable
private fun Main(c: SupabaseClient, openSettings: () -> Unit) {
    val status by c.auth.sessionStatus.collectAsState()
    Crossfade(status is SessionStatus.Authenticated, animationSpec = tween(220), label = "session") { signedIn ->
        when {
            signedIn -> Home(openSettings)
            status is SessionStatus.NotAuthenticated -> {
                var reset by remember { mutableStateOf(false) }
                if (reset) ResetScreen { reset = false } else AuthScreen(openSettings) { reset = true }
            }
            else -> FullScreenLoading()
        }
    }
}
