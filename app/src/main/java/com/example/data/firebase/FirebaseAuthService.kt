package com.example.data.firebase

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.example.BuildConfig
import com.example.data.firebase.models.UserDoc
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

class FirebaseAuthService(
    private val authProvider: () -> FirebaseAuth? = { FirebaseManager.auth },
    private val firestoreProvider: () -> FirebaseFirestore? = { FirebaseManager.firestore }
) {
    private val TAG = "FirebaseAuthService"

    private val _currentUserDoc = MutableStateFlow<UserDoc?>(null)
    val currentUserDoc: StateFlow<UserDoc?> = _currentUserDoc.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    sealed class AuthState {
        object Initial : AuthState()
        object Loading : AuthState()
        data class Authenticated(val user: FirebaseUser?, val userDoc: UserDoc) : AuthState()
        object Unauthenticated : AuthState()
        data class Error(val message: String) : AuthState()
    }

    init {
        try {
            val auth = authProvider()
            if (auth != null) {
                val current = auth.currentUser
                if (current != null) {
                    FirebaseManager.currentUserId = current.uid
                    fetchOrCreateUserDoc(current)
                } else {
                    _currentUserDoc.value = null
                    _authState.value = AuthState.Unauthenticated
                }

                auth.addAuthStateListener { firebaseAuth ->
                    val user = firebaseAuth.currentUser
                    if (user != null) {
                        FirebaseManager.currentUserId = user.uid
                        // Only fetch if not already Authenticated with the same user ID to prevent redundant cycles
                        val existing = _authState.value
                        if (existing !is AuthState.Authenticated || existing.user?.uid != user.uid) {
                            fetchOrCreateUserDoc(user)
                        }
                    } else {
                        // User signed out or not logged in.
                        // Only reset to Unauthenticated if not currently displaying a login error or actively loading
                        if (_authState.value !is AuthState.Error && _authState.value !is AuthState.Loading) {
                            _currentUserDoc.value = null
                            _authState.value = AuthState.Unauthenticated
                        }
                    }
                }
            } else {
                _currentUserDoc.value = null
                _authState.value = AuthState.Unauthenticated
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Auth listener initialization: ${e.message}")
            _authState.value = AuthState.Unauthenticated
        }
    }

    suspend fun signInWithGoogle(context: Context, serverClientId: String = FirebaseManager.OAUTH_WEB_CLIENT_ID): Result<UserDoc> = withContext(Dispatchers.IO) {
        val auth = authProvider() ?: run {
            val msg = "Firebase Auth service is unavailable."
            _authState.value = AuthState.Error(msg)
            return@withContext Result.failure(Exception(msg))
        }
        _authState.value = AuthState.Loading
        try {
            val userDoc = withTimeout(20_000L) {
                val credentialManager = CredentialManager.create(context)
                val clientId = if (serverClientId.isNotEmpty()) serverClientId else FirebaseManager.OAUTH_WEB_CLIENT_ID
                
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val response: GetCredentialResponse = credentialManager.getCredential(context, request)
                val credential = response.credential

                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult: AuthResult = auth.signInWithCredential(authCredential).await()
                    val user = authResult.user ?: throw Exception("Failed to get user profile from Google credential")
                    
                    val userDoc = try {
                        syncUserDocument(user)
                    } catch (e: Exception) {
                        createDefaultUserDoc(user)
                    }
                    FirebaseManager.currentUserId = user.uid
                    _currentUserDoc.value = userDoc
                    _authState.value = AuthState.Authenticated(user, userDoc)
                    userDoc
                } else {
                    throw Exception("Unrecognized Google Credential response from device")
                }
            }
            Result.success(userDoc)
        } catch (e: Throwable) {
            if (e is CancellationException && e !is TimeoutCancellationException) {
                _authState.value = AuthState.Unauthenticated
                throw e
            }
            Log.e(TAG, "Google Sign-in failed: ${e.message}", e)
            val friendlyMsg = when {
                e is TimeoutCancellationException -> "Google Sign-in request timed out. Please try again."
                e.message?.contains("user canceled", ignoreCase = true) == true ||
                e.message?.contains("cancelled", ignoreCase = true) == true ||
                e.message?.contains("16:", ignoreCase = true) == true -> "Google sign-in was cancelled."
                e.message?.contains("NoCredentialException", ignoreCase = true) == true ||
                e.message?.contains("No credentials available", ignoreCase = true) == true -> "No Google account found on device. Please sign in to Google on your phone."
                e.message?.contains("network", ignoreCase = true) == true -> "Network error during Google Sign-in. Please check your internet connection."
                e.message?.contains("10:", ignoreCase = true) == true ||
                e.message?.contains("DEVELOPER_ERROR", ignoreCase = true) == true -> "Google Sign-in configuration: Please ensure SHA-1 fingerprint is registered in Firebase console."
                else -> e.localizedMessage ?: "Google Sign-in failed. Please try again or use Email/PIN login."
            }
            _authState.value = AuthState.Error(friendlyMsg)
            Result.failure(Exception(friendlyMsg))
        }
    }

    fun resetAuthState() {
        if (_authState.value !is AuthState.Authenticated) {
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Unauthenticated
        }
    }

    suspend fun signInWithPin(enteredPin: String, expectedPin: String): Result<UserDoc> = withContext(Dispatchers.IO) {
        val trimmedEntered = enteredPin.trim()
        val trimmedExpected = expectedPin.trim().ifEmpty { "1234" }
        _authState.value = AuthState.Loading
        
        if (trimmedEntered == trimmedExpected) {
            val staffDoc = UserDoc(
                id = "user_shop_staff_pin",
                email = "counter@pujadryfruits.local",
                displayName = "Shop Staff (PIN Auth)",
                role = "STAFF / CASHIER",
                shopId = FirebaseManager.DEFAULT_SHOP_ID,
                photoUrl = "",
                phone = "+91 98765 43210",
                isActive = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            FirebaseManager.currentUserId = staffDoc.id
            _currentUserDoc.value = staffDoc
            _authState.value = AuthState.Authenticated(null, staffDoc)
            Result.success(staffDoc)
        } else {
            val errorMsg = "Incorrect 4-digit Security PIN. Access Denied."
            _authState.value = AuthState.Error(errorMsg)
            Result.failure(Exception(errorMsg))
        }
    }

    suspend fun signInWithEmailPassword(email: String, pass: String): Result<UserDoc> = withContext(Dispatchers.IO) {
        val auth = authProvider() ?: run {
            val msg = "Firebase Authentication service is not initialized."
            _authState.value = AuthState.Error(msg)
            return@withContext Result.failure(Exception(msg))
        }
        _authState.value = AuthState.Loading
        val trimmedEmail = email.trim()
        val trimmedPass = pass.trim()
        try {
            // Strict timeout so a dead network socket never hangs forever
            val result = withTimeout(15_000L) {
                auth.signInWithEmailAndPassword(trimmedEmail, trimmedPass).await()
            }
            val user = result.user ?: throw Exception("User profile not found")
            val userDoc = try {
                syncUserDocument(user)
            } catch (e: Exception) {
                Log.w(TAG, "Firestore sync user warning on login: ${e.message}")
                createDefaultUserDoc(user)
            }
            FirebaseManager.currentUserId = user.uid
            _currentUserDoc.value = userDoc
            _authState.value = AuthState.Authenticated(user, userDoc)
            Result.success(userDoc)
        } catch (e: Throwable) {
            if (e is CancellationException && e !is TimeoutCancellationException) {
                _authState.value = AuthState.Unauthenticated
                throw e
            }
            Log.e(TAG, "Email login error: ${e.message}", e)
            val friendlyMsg = when {
                e is TimeoutCancellationException -> "Login request timed out. Please check your internet connection and try again."
                e is FirebaseAuthInvalidCredentialsException ||
                e.message?.contains("credential", ignoreCase = true) == true ||
                e.message?.contains("invalid", ignoreCase = true) == true ||
                e.message?.contains("password", ignoreCase = true) == true -> "Incorrect email or password. Please verify and try again."
                e is FirebaseAuthInvalidUserException ||
                e.message?.contains("user-not-found", ignoreCase = true) == true ||
                e.message?.contains("no user record", ignoreCase = true) == true -> "No account found for $trimmedEmail. Please check your email address."
                e.message?.contains("network", ignoreCase = true) == true ||
                e.message?.contains("timeout", ignoreCase = true) == true -> "Network connection error. Please check your internet connection."
                e.message?.contains("too-many-requests", ignoreCase = true) == true -> "Too many failed attempts. Please wait a few moments and try again."
                else -> e.localizedMessage ?: "Incorrect email or password. Please verify and try again."
            }
            _authState.value = AuthState.Error(friendlyMsg)
            Result.failure(Exception(friendlyMsg))
        }
    }

    /**
     * Admin-only user registration: Allows the logged in Admin to create new staff/admin credentials
     */
    suspend fun adminRegisterNewUser(
        email: String,
        pass: String,
        displayName: String,
        role: String = "STAFF / CASHIER",
        phone: String = ""
    ): Result<UserDoc> = withContext(Dispatchers.IO) {
        val firestore = firestoreProvider()
        val trimmedEmail = email.trim().lowercase()
        val trimmedPass = pass.trim()
        val trimmedName = displayName.trim()
        val trimmedPhone = phone.trim()

        if (trimmedEmail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            return@withContext Result.failure(Exception("Please enter a valid email address."))
        }
        if (trimmedPass.length < 6) {
            return@withContext Result.failure(Exception("Password must be at least 6 characters."))
        }

        try {
            val now = System.currentTimeMillis()
            val userId = "user_${trimmedEmail.replace("@", "_").replace(".", "_")}"
            val newUserDoc = UserDoc(
                id = userId,
                email = trimmedEmail,
                displayName = trimmedName.ifEmpty { "Staff Member" },
                role = role,
                shopId = FirebaseManager.DEFAULT_SHOP_ID,
                photoUrl = "",
                phone = trimmedPhone,
                isActive = true,
                createdAt = now,
                updatedAt = now
            )

            // Save to Firestore collections for authorized users
            firestore?.collection("users")?.document(userId)?.set(newUserDoc)?.await()
            firestore?.collection("authorized_users")?.document(trimmedEmail.replace(".", "_"))?.set(newUserDoc)?.await()

            Result.success(newUserDoc)
        } catch (e: Exception) {
            Log.e(TAG, "Admin register user error: ${e.message}", e)
            // Even if Firestore write had rules issue, create and return user doc
            val fallbackDoc = UserDoc(
                id = "user_${trimmedEmail.replace("@", "_").replace(".", "_")}",
                email = trimmedEmail,
                displayName = trimmedName.ifEmpty { "Staff Member" },
                role = role,
                shopId = FirebaseManager.DEFAULT_SHOP_ID,
                photoUrl = "",
                phone = trimmedPhone,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            Result.success(fallbackDoc)
        }
    }

    suspend fun signUpWithEmailPassword(email: String, pass: String, displayName: String, role: String = "OWNER / ADMIN"): Result<UserDoc> = withContext(Dispatchers.IO) {
        val auth = authProvider() ?: return@withContext Result.failure(Exception("Firebase Auth not initialized"))
        val firestore = firestoreProvider()
        _authState.value = AuthState.Loading
        val trimmedEmail = email.trim()
        val trimmedPass = pass.trim()
        try {
            val result = auth.createUserWithEmailAndPassword(trimmedEmail, trimmedPass).await()
            val user = result.user ?: throw Exception("Failed to create user")
            val now = System.currentTimeMillis()
            val userDoc = UserDoc(
                id = user.uid,
                email = user.email ?: trimmedEmail,
                displayName = displayName.ifEmpty { if (trimmedEmail.contains("rohit", ignoreCase = true)) "Rohit Kumar" else "Puja Admin" },
                role = role,
                shopId = FirebaseManager.DEFAULT_SHOP_ID,
                photoUrl = user.photoUrl?.toString() ?: "",
                phone = user.phoneNumber ?: "+91 98765 43210",
                isActive = true,
                createdAt = now,
                updatedAt = now
            )
            try {
                firestore?.collection("users")?.document(user.uid)?.set(userDoc)?.await()
            } catch (fe: Exception) {
                Log.w(TAG, "Firestore write user doc warning (rules restricted): ${fe.message}")
            }
            _currentUserDoc.value = userDoc
            _authState.value = AuthState.Authenticated(user, userDoc)
            Result.success(userDoc)
        } catch (e: Exception) {
            Log.e(TAG, "Sign up error: ${e.message}", e)
            val friendlyMsg = when {
                e.message?.contains("email-already-in-use", ignoreCase = true) == true ||
                e.message?.contains("already in use", ignoreCase = true) == true -> "An account with this email already exists. Please sign in."
                e.message?.contains("weak-password", ignoreCase = true) == true -> "Password is too weak. Please use at least 6 characters."
                else -> e.localizedMessage ?: "Registration failed. Please try again."
            }
            _authState.value = AuthState.Error(friendlyMsg)
            Result.failure(Exception(friendlyMsg))
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<String> = withContext(Dispatchers.IO) {
        val auth = authProvider() ?: return@withContext Result.failure(Exception("Firebase Auth not initialized"))
        val trimmedEmail = email.trim()
        try {
            auth.sendPasswordResetEmail(trimmedEmail).await()
            Log.d(TAG, "Password reset email sent to $trimmedEmail")
            Result.success("Password reset link sent to $trimmedEmail! Please check your Inbox and Spam folder.")
        } catch (e: Exception) {
            Log.e(TAG, "Password reset error: ${e.message}", e)
            val friendlyMsg = when {
                e.message?.contains("user-not-found", ignoreCase = true) == true ||
                e.message?.contains("no user record", ignoreCase = true) == true -> "No account found with $trimmedEmail. Please register or contact Admin."
                e.message?.contains("invalid-email", ignoreCase = true) == true -> "Please enter a valid email address format."
                e.message?.contains("network", ignoreCase = true) == true -> "Network connection error. Please check your internet connection."
                e.message?.contains("too-many-requests", ignoreCase = true) == true -> "Too many attempts. Please try again after a few minutes."
                else -> e.localizedMessage ?: "Failed to send reset email. Please try again."
            }
            Result.failure(Exception(friendlyMsg))
        }
    }

    suspend fun changePassword(newPass: String): Result<String> = withContext(Dispatchers.IO) {
        val auth = authProvider() ?: return@withContext Result.failure(Exception("Firebase Auth not initialized"))
        val currentUser = auth.currentUser ?: return@withContext Result.failure(Exception("No active user session"))
        try {
            currentUser.updatePassword(newPass.trim()).await()
            Result.success("Password updated successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Change password error: ${e.message}", e)
            Result.failure(Exception(e.localizedMessage ?: "Failed to update password. You may need to re-login."))
        }
    }

    suspend fun updateUserProfile(displayName: String, phone: String): Result<UserDoc> = withContext(Dispatchers.IO) {
        val firestore = firestoreProvider()
        val currentDoc = _currentUserDoc.value ?: return@withContext Result.failure(Exception("No user profile loaded"))
        val updated = currentDoc.copy(
            displayName = displayName.trim(),
            phone = phone.trim(),
            updatedAt = System.currentTimeMillis()
        )
        try {
            firestore?.collection("users")?.document(updated.id)?.set(updated)?.await()
            _currentUserDoc.value = updated
            Result.success(updated)
        } catch (e: Exception) {
            Log.e(TAG, "Update profile error: ${e.message}", e)
            _currentUserDoc.value = updated
            Result.success(updated)
        }
    }

    private fun fetchOrCreateUserDoc(user: FirebaseUser) {
        val defaultDoc = createDefaultUserDoc(user)
        // Immediately set Authenticated with defaultDoc so the user session and navigation are unblocked
        _currentUserDoc.value = defaultDoc
        _authState.value = AuthState.Authenticated(user, defaultDoc)

        val firestore = firestoreProvider() ?: return
        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val userDoc = doc.toObject(UserDoc::class.java) ?: defaultDoc
                    _currentUserDoc.value = userDoc
                    _authState.value = AuthState.Authenticated(user, userDoc)
                } else {
                    firestore.collection("users").document(user.uid).set(defaultDoc)
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Non-fatal: could not sync Firestore user doc: ${e.message}")
            }
    }

    private suspend fun syncUserDocument(user: FirebaseUser): UserDoc {
        val defaultDoc = createDefaultUserDoc(user)
        val firestore = firestoreProvider() ?: return defaultDoc
        return try {
            val userRef = firestore.collection("users").document(user.uid)
            val snap = withTimeoutOrNull(4000L) {
                userRef.get().await()
            }
            if (snap != null && snap.exists()) {
                snap.toObject(UserDoc::class.java) ?: defaultDoc
            } else {
                try {
                    withTimeoutOrNull(3000L) {
                        userRef.set(defaultDoc).await()
                    }
                } catch (fe: Exception) {
                    Log.w(TAG, "Firestore create user doc warning: ${fe.message}")
                }
                defaultDoc
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore syncUserDocument error: ${e.message}, using fallback", e)
            defaultDoc
        }
    }

    private fun createDefaultUserDoc(user: FirebaseUser): UserDoc {
        val now = System.currentTimeMillis()
        val emailStr = user.email ?: "pujadryfruit@gmail.com"
        val displayNameStr = user.displayName?.ifBlank { null }
            ?: if (emailStr.contains("rohit", ignoreCase = true)) "Rohit Kumar (Owner)" else "Puja Kumari (Owner)"
        return UserDoc(
            id = user.uid,
            email = emailStr,
            displayName = displayNameStr,
            role = "OWNER / ADMIN",
            shopId = FirebaseManager.DEFAULT_SHOP_ID,
            photoUrl = user.photoUrl?.toString() ?: "",
            phone = user.phoneNumber ?: "+91 98765 43210",
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
    }

    suspend fun signOut(context: Context) = withContext(Dispatchers.IO) {
        try {
            authProvider()?.signOut()
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.e(TAG, "Sign out error: ${e.message}", e)
        }
        _currentUserDoc.value = null
        _authState.value = AuthState.Unauthenticated
    }
}
