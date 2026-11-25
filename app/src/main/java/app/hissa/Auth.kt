package app.hissa

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
private fun AuthColumn(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    content = content,
)

@Composable
private fun BackRow(onBack: () -> Unit) = Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
    BackHandler(onBack = onBack)
    IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
}

@Composable
fun AuthScreen(openSettings: () -> Unit, onForgot: () -> Unit) {
    var mode by remember { mutableIntStateOf(0) } // 0 sign in, 1 sign up
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val run = rememberRunner(busy = { busy = it })
    if (confirming) { ConfirmScreen(email) { confirming = false }; return }

    val signup = mode == 1
    val emailErr = Check.email(email); val passErr = if (signup) Check.password(pass) else null; val nameErr = Check.personName(name)
    val valid = email.isNotBlank() && emailErr == null && pass.isNotEmpty() && passErr == null && (!signup || (name.isNotBlank() && nameErr == null))
    AuthColumn {
        Spacer(Modifier.height(48.dp))
        LogoMark(60.dp)
        Spacer(Modifier.height(20.dp))
        Text("Hissa", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(4.dp))
        Text("Hisaab barabar, dosti kayam.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Segmented(listOf("Sign in", "Sign up"), mode, { mode = it })
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (signup) HissaField(name, { name = it.take(60) }, "Full name", error = nameErr)
            HissaField(email, { email = it.replace(" ", "").take(254) }, "Email", keyboard = KeyboardType.Email, error = emailErr)
            HissaField(
                pass, { pass = it.take(72) }, "Password", password = !show, error = passErr,
                trailing = {
                    IconButton({ show = !show }) {
                        Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = if (show) "Hide password" else "Show password")
                    }
                },
            )
        }
        if (!signup) Row(Modifier.fillMaxWidth(), Arrangement.End) { TextButton(onForgot) { Text("Forgot password?") } } else Spacer(Modifier.height(16.dp))
        PrimaryButton(if (signup) "Create account" else "Sign in", enabled = valid, loading = busy, onClick = {
            run {
                if (!signup) {
                    try { Api.signIn(email, pass) } catch (e: Exception) {
                        // Account exists but was never confirmed: send a fresh code and ask for it.
                        if (e.message?.contains("not confirmed", ignoreCase = true) == true) {
                            Api.resendSignupCode(email); confirming = true
                        } else throw e
                    }
                } else if (!Api.signUp(name, email, pass)) confirming = true
            }
        })
        Spacer(Modifier.height(16.dp))
        TextButton(openSettings, Modifier.align(Alignment.CenterHorizontally)) {
            Text("Server settings", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun ConfirmScreen(email: String, onBack: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val run = rememberRunner(busy = { busy = it })
    val snack = LocalSnack.current
    AuthColumn {
        BackRow(onBack)
        Spacer(Modifier.height(24.dp))
        Text("Check your email", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter the code we sent to $email.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        HissaField(code, { code = it.filter(Char::isDigit).take(10) }, "Code", keyboard = KeyboardType.Number, centered = true)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) { Text("Nothing yet?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); InfoTip("It can take a minute and may land in spam.") }
        PrimaryButton("Confirm", enabled = code.length >= 6, loading = busy, onClick = { run { Api.confirmSignup(email, code) } })
        Spacer(Modifier.height(8.dp))
        TextButton({ run { Api.resendSignupCode(email); snack.say("New code sent") } }, Modifier.align(Alignment.CenterHorizontally)) {
            Text("Resend code")
        }
    }
}

@Composable
fun ResetScreen(onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val run = rememberRunner(busy = { busy = it })
    AuthColumn {
        BackRow(onBack)
        Spacer(Modifier.height(24.dp))
        Text("Reset password", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            if (sent) "Enter the code from your email and choose a new password."
            else "We will email you a code.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HissaField(email, { email = it.replace(" ", "").take(254) }, "Email", keyboard = KeyboardType.Email, error = Check.email(email))
            if (sent) {
                HissaField(code, { code = it.filter(Char::isDigit).take(10) }, "Code", keyboard = KeyboardType.Number, centered = true)
                HissaField(pass, { pass = it.take(72) }, "New password (8+)", password = true, error = Check.password(pass))
            }
        }
        Spacer(Modifier.height(20.dp))
        val ready = if (sent) code.length >= 6 && Check.password(pass) == null && pass.isNotEmpty() else email.isNotBlank() && Check.email(email) == null
        PrimaryButton(if (sent) "Set new password" else "Email me a code", enabled = ready, loading = busy, onClick = {
            run { if (!sent) { Api.sendResetCode(email); sent = true } else Api.resetPassword(email, code, pass) }
        })
    }
}

@Composable
fun SetupScreen(canCancel: Boolean, onDone: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    val run = rememberRunner()
    AuthColumn {
        if (canCancel) BackRow(onDone) else Spacer(Modifier.height(48.dp))
        Spacer(Modifier.height(16.dp))
        LogoMark(52.dp)
        Spacer(Modifier.height(20.dp))
        Text("Connect to your server", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Paste the Project URL and the anon / publishable key from Supabase, under Project Settings, API.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HissaField(url, { url = it }, "Project URL", keyboard = KeyboardType.Uri)
            HissaField(key, { key = it }, "Anon / publishable key", singleLine = false)
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Connect", enabled = url.isNotBlank() && key.isNotBlank(), onClick = { run { Api.configure(url, key); onDone() } })
    }
}
