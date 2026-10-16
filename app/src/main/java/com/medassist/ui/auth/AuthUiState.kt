package com.medassist.ui.auth

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()
    data class OtpSent(val verificationId: String) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}
