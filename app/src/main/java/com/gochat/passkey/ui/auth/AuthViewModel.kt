package com.gochat.passkey.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gochat.passkey.domain.AuthInteractor
import com.gochat.passkey.domain.AuthResult
import com.gochat.passkey.domain.EnrollSession
import com.gochat.passkey.domain.SessionTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthScreen {
    Home,
    Phone,
    Otp,
    CreatePasskey,
    PasskeyLogin,
    Success,
}

data class AuthUiState(
    val screen: AuthScreen = AuthScreen.Home,
    val msisdn: String = "",
    val otp: String = "",
    val loading: Boolean = false,
    val message: String? = null,
    val snackbar: String? = null,
    val canRecoverWithOtp: Boolean = false,
    val enrollSession: EnrollSession? = null,
    val session: SessionTokens? = null,
    val hasLocalCredentials: Boolean = false,
)

class AuthViewModel(
    private val interactor: AuthInteractor,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AuthUiState(
            session = interactor.currentSession(),
            hasLocalCredentials = interactor.hasRegisteredCredentials(),
            screen = if (interactor.currentSession() != null) AuthScreen.Success else AuthScreen.Home,
        ),
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun startFirstLogin() {
        _state.update {
            it.copy(
                screen = AuthScreen.Phone,
                message = null,
                canRecoverWithOtp = false,
                snackbar = null,
            )
        }
    }

    fun startLaterLogin() {
        _state.update {
            it.copy(
                screen = AuthScreen.PasskeyLogin,
                message = null,
                canRecoverWithOtp = false,
                snackbar = null,
            )
        }
    }

    fun goHome() {
        _state.update {
            it.copy(
                screen = AuthScreen.Home,
                message = null,
                loading = false,
                canRecoverWithOtp = false,
                hasLocalCredentials = interactor.hasRegisteredCredentials(),
            )
        }
    }

    fun onMsisdnChange(value: String) {
        _state.update { it.copy(msisdn = value, message = null) }
    }

    fun onOtpChange(value: String) {
        _state.update { it.copy(otp = value, message = null) }
    }

    fun requestOtp() {
        val msisdn = _state.value.msisdn
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            when (val result = interactor.requestOtp(msisdn)) {
                is AuthResult.Ok -> _state.update {
                    it.copy(loading = false, screen = AuthScreen.Otp, message = "Demo OTP: 123456")
                }
                is AuthResult.Err -> _state.update {
                    it.copy(loading = false, message = result.message)
                }
            }
        }
    }

    fun verifyOtp() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            when (val result = interactor.verifyOtp(s.msisdn, s.otp)) {
                is AuthResult.Ok -> _state.update {
                    it.copy(
                        loading = false,
                        enrollSession = result.value,
                        screen = AuthScreen.CreatePasskey,
                        message = null,
                    )
                }
                is AuthResult.Err -> _state.update {
                    it.copy(loading = false, message = result.message)
                }
            }
        }
    }

    fun createPasskey() {
        val enroll = _state.value.enrollSession ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null, snackbar = null) }
            when (val result = interactor.registerPasskey(enroll)) {
                is AuthResult.Ok -> _state.update {
                    it.copy(
                        loading = false,
                        session = result.value,
                        screen = AuthScreen.Success,
                        snackbar = "Passkey created",
                        hasLocalCredentials = true,
                        message = null,
                    )
                }
                is AuthResult.Err -> _state.update {
                    it.copy(loading = false, message = result.message)
                }
            }
        }
    }

    fun signInWithPasskey() {
        viewModelScope.launch {
            _state.update {
                it.copy(loading = true, message = null, canRecoverWithOtp = false)
            }
            when (val result = interactor.loginWithPasskey()) {
                is AuthResult.Ok -> _state.update {
                    it.copy(
                        loading = false,
                        session = result.value,
                        screen = AuthScreen.Success,
                        message = null,
                        snackbar = "Login successful — no OTP",
                    )
                }
                is AuthResult.Err -> _state.update {
                    it.copy(
                        loading = false,
                        message = result.message,
                        canRecoverWithOtp = result.canRecoverWithOtp,
                    )
                }
            }
        }
    }

    fun recoverWithOtp() {
        startFirstLogin()
    }

    fun signOut() {
        interactor.signOut()
        _state.update {
            AuthUiState(
                hasLocalCredentials = interactor.hasRegisteredCredentials(),
                screen = AuthScreen.Home,
            )
        }
    }

    fun consumeSnackbar() {
        _state.update { it.copy(snackbar = null) }
    }

    class Factory(private val interactor: AuthInteractor) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(interactor) as T
        }
    }
}
