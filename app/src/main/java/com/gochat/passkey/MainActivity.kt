package com.gochat.passkey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gochat.passkey.data.CredentialManagerPasskeyGateway
import com.gochat.passkey.ui.auth.AuthApp
import com.gochat.passkey.ui.auth.AuthViewModel
import com.gochat.passkey.ui.theme.NChatPasskeyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as NChatPasskeyApp
        val ceremony = CredentialManagerPasskeyGateway(this)
        val interactor = app.authInteractor(ceremony)

        setContent {
            NChatPasskeyTheme {
                val vm: AuthViewModel = viewModel(
                    factory = AuthViewModel.Factory(interactor),
                )
                AuthApp(viewModel = vm)
            }
        }
    }
}
