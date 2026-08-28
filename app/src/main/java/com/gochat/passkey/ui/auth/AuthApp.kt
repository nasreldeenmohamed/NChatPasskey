package com.gochat.passkey.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gochat.passkey.BuildConfig

@Composable
fun AuthApp(viewModel: AuthViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbar) {
        val msg = state.snackbar ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeSnackbar()
    }

    androidx.compose.material3.Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = "NChat Passkey",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "SDD-AUTH-002 concept demo · RP: ${BuildConfig.RP_ID}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            when (state.screen) {
                AuthScreen.Home -> HomeScreen(
                    hasCredentials = state.hasLocalCredentials,
                    onFirstLogin = viewModel::startFirstLogin,
                    onLaterLogin = viewModel::startLaterLogin,
                )
                AuthScreen.Phone -> PhoneScreen(
                    msisdn = state.msisdn,
                    loading = state.loading,
                    message = state.message,
                    onMsisdnChange = viewModel::onMsisdnChange,
                    onRequestOtp = viewModel::requestOtp,
                    onBack = viewModel::goHome,
                )
                AuthScreen.Otp -> OtpScreen(
                    otp = state.otp,
                    loading = state.loading,
                    message = state.message,
                    onOtpChange = viewModel::onOtpChange,
                    onVerify = viewModel::verifyOtp,
                    onBack = viewModel::goHome,
                )
                AuthScreen.CreatePasskey -> CreatePasskeyScreen(
                    loading = state.loading,
                    message = state.message,
                    onCreate = viewModel::createPasskey,
                    onBack = viewModel::goHome,
                )
                AuthScreen.PasskeyLogin -> PasskeyLoginScreen(
                    loading = state.loading,
                    message = state.message,
                    canRecoverWithOtp = state.canRecoverWithOtp,
                    onSignIn = viewModel::signInWithPasskey,
                    onRecover = viewModel::recoverWithOtp,
                    onBack = viewModel::goHome,
                )
                AuthScreen.Success -> SuccessScreen(
                    session = state.session,
                    onSignOut = viewModel::signOut,
                    onHome = viewModel::goHome,
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    hasCredentials: Boolean,
    onFirstLogin: () -> Unit,
    onLaterLogin: () -> Unit,
) {
    Text(
        "OTP for first verification, passkey for later passwordless login.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(20.dp))
    Button(onClick = onFirstLogin, modifier = Modifier.fillMaxWidth()) {
        Text("First login (OTP + passkey)")
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = onLaterLogin,
        enabled = hasCredentials,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Later login (passkey)")
    }
    if (!hasCredentials) {
        Spacer(Modifier.height(8.dp))
        Text(
            "No local credential record yet — complete first login first.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PhoneScreen(
    msisdn: String,
    loading: Boolean,
    message: String?,
    onMsisdnChange: (String) -> Unit,
    onRequestOtp: () -> Unit,
    onBack: () -> Unit,
) {
    Text("Enter phone number", fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = msisdn,
        onValueChange = onMsisdnChange,
        label = { Text("MSISDN") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    MessageBlock(message, loading)
    Button(
        onClick = onRequestOtp,
        enabled = !loading && msisdn.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Request OTP")
    }
    TextButton(onClick = onBack) { Text("Back") }
}

@Composable
private fun OtpScreen(
    otp: String,
    loading: Boolean,
    message: String?,
    onOtpChange: (String) -> Unit,
    onVerify: () -> Unit,
    onBack: () -> Unit,
) {
    Text("Enter OTP", fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = otp,
        onValueChange = onOtpChange,
        label = { Text("OTP") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    MessageBlock(message, loading)
    Button(
        onClick = onVerify,
        enabled = !loading && otp.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Verify OTP")
    }
    TextButton(onClick = onBack) { Text("Back") }
}

@Composable
private fun CreatePasskeyScreen(
    loading: Boolean,
    message: String?,
    onCreate: () -> Unit,
    onBack: () -> Unit,
) {
    Text("Create a passkey", fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Text(
        "Sign in faster next time using your screen lock. Private key stays in Google Password Manager (or your chosen provider).",
        style = MaterialTheme.typography.bodyMedium,
    )
    MessageBlock(message, loading)
    Button(
        onClick = onCreate,
        enabled = !loading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Create a passkey")
    }
    TextButton(onClick = onBack) { Text("Back") }
}

@Composable
private fun PasskeyLoginScreen(
    loading: Boolean,
    message: String?,
    canRecoverWithOtp: Boolean,
    onSignIn: () -> Unit,
    onRecover: () -> Unit,
    onBack: () -> Unit,
) {
    Text("Sign in with a passkey", fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Text(
        "No OTP — Credential Manager will ask the provider to sign a one-time challenge.",
        style = MaterialTheme.typography.bodyMedium,
    )
    MessageBlock(message, loading)
    Button(
        onClick = onSignIn,
        enabled = !loading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Sign in with passkey")
    }
    if (canRecoverWithOtp) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRecover, modifier = Modifier.fillMaxWidth()) {
            Text("Use OTP recovery")
        }
    }
    TextButton(onClick = onBack) { Text("Back") }
}

@Composable
private fun SuccessScreen(
    session: com.gochat.passkey.domain.SessionTokens?,
    onSignOut: () -> Unit,
    onHome: () -> Unit,
) {
    Text("Authenticated", fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
    if (session != null) {
        Text("MSISDN: ${session.msisdn}")
        Text("Access: ${session.accessToken.take(24)}…", style = MaterialTheme.typography.bodySmall)
        Text("Refresh: ${session.refreshToken.take(24)}…", style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(20.dp))
    OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
        Text("Home")
    }
    Spacer(Modifier.height(8.dp))
    Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
        Text("Sign out (clear session)")
    }
}

@Composable
private fun MessageBlock(message: String?, loading: Boolean) {
    Spacer(Modifier.height(12.dp))
    if (loading) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
        }
    }
    if (!message.isNullOrBlank()) {
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
    } else {
        Spacer(Modifier.height(8.dp))
    }
}
