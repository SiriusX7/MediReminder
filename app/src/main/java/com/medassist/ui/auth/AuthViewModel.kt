package com.medassist.ui.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.medassist.data.local.UserProfile
import com.medassist.data.local.UserProfileDao
import com.medassist.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class AuthViewModel(
    private val repository: AuthRepository,
    private val userProfileDao: UserProfileDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun signIn(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("Email and password cannot be empty")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.signInWithEmail(email, pass)
            result.fold(
                onSuccess = { 
                    checkAndCreateProfile(it?.uid, it?.email, it?.displayName)
                    _uiState.value = AuthUiState.Success 
                },
                onFailure = { _uiState.value = AuthUiState.Error(mapError(it)) }
            )
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.signInWithGoogle(idToken)
            result.fold(
                onSuccess = {
                    checkAndCreateProfile(it?.uid, it?.email, it?.displayName)
                    _uiState.value = AuthUiState.Success
                },
                onFailure = { _uiState.value = AuthUiState.Error(mapError(it)) }
            )
        }
    }

    fun sendOtp(phoneNumber: String, activity: Activity) {
        if (phoneNumber.isBlank()) {
            _uiState.value = AuthUiState.Error("Phone number cannot be empty")
            return
        }
        _uiState.value = AuthUiState.Loading
        
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                signInWithPhoneCredential(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                _uiState.value = AuthUiState.Error(e.localizedMessage ?: "Verification failed")
            }

            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                _uiState.value = AuthUiState.OtpSent(verificationId)
            }
        }

        repository.startPhoneVerification(phoneNumber, activity, callbacks)
    }

    fun verifyOtp(verificationId: String, otp: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.signInWithPhone(verificationId, otp)
            result.fold(
                onSuccess = {
                    checkAndCreateProfile(it?.uid, null, it?.phoneNumber)
                    _uiState.value = AuthUiState.Success
                },
                onFailure = { _uiState.value = AuthUiState.Error(mapError(it)) }
            )
        }
    }

    private fun signInWithPhoneCredential(credential: PhoneAuthCredential) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.signInWithPhone(credential)
            result.fold(
                onSuccess = {
                    checkAndCreateProfile(it?.uid, null, it?.phoneNumber)
                    _uiState.value = AuthUiState.Success
                },
                onFailure = { _uiState.value = AuthUiState.Error(mapError(it)) }
            )
        }
    }

    private suspend fun checkAndCreateProfile(uid: String?, email: String?, displayName: String?) {
        uid?.let { id ->
            val existingProfile = userProfileDao.getUserProfile(id)
            if (existingProfile == null) {
                val names = displayName?.split(" ", limit = 2) ?: listOf("", "")
                val profile = UserProfile(
                    uid = id,
                    email = email ?: "",
                    firstName = names.getOrNull(0) ?: "",
                    lastName = names.getOrNull(1) ?: ""
                )
                userProfileDao.insert(profile)
            }
        }
    }

    fun signUp(name: String, email: String, pass: String, confirmPass: String) {
        if (name.isBlank() || email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("All fields are required")
            return
        }
        if (pass != confirmPass) {
            _uiState.value = AuthUiState.Error("Passwords do not match")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.signUpWithEmail(email, pass)
            result.fold(
                onSuccess = { user ->
                    user?.let {
                        val names = name.split(" ", limit = 2)
                        val firstName = names.getOrNull(0) ?: ""
                        val lastName = names.getOrNull(1) ?: ""
                        val profile = UserProfile(
                            uid = it.uid,
                            email = email,
                            firstName = firstName,
                            lastName = lastName
                        )
                        userProfileDao.insert(profile)
                    }
                    _uiState.value = AuthUiState.Success
                },
                onFailure = { _uiState.value = AuthUiState.Error(mapError(it)) }
            )
        }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter your email")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.sendPasswordReset(email)
            result.fold(
                onSuccess = { _uiState.value = AuthUiState.Success },
                onFailure = { _uiState.value = AuthUiState.Error(mapError(it)) }
            )
        }
    }

    fun clearState() {
        _uiState.value = AuthUiState.Idle
    }

    private fun mapError(e: Throwable): String {
        return when (e) {
            is FirebaseAuthException -> {
                when (e.errorCode) {
                    "ERROR_INVALID_EMAIL" -> "Invalid email address format."
                    "ERROR_WRONG_PASSWORD" -> "Incorrect password."
                    "ERROR_USER_NOT_FOUND" -> "No user found with this email."
                    "ERROR_WEAK_PASSWORD" -> "The password is too weak."
                    "ERROR_EMAIL_ALREADY_IN_USE" -> "This email is already in use."
                    else -> e.localizedMessage ?: "Authentication failed."
                }
            }
            else -> e.localizedMessage ?: "An unexpected error occurred."
        }
    }
}
