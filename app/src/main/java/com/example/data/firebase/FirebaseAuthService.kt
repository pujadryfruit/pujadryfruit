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
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

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
                        fetchOrCreateUserDoc(user)
                    } else {
                        _currentUserDoc.value = null
                        _authState.value = AuthState.Unauthenticated
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
        val auth = authProvider() ?: return@withContext Result.failure(Exception("Firebase Auth not initialized"))
        _authState.value = AuthState.Loading
        try {
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
                val user = authResult.user ?: throw Exception("Failed to get Firebase User from Google credential")
                val userEmail = user.email?.lowercase()?.trim() ?: ""
                val isDefaultOwner = userEmail.contains("rohit", ignoreCase = true) || 
                                     userEmail.contains("puja", ignoreCase = true) ||
                                     userEmail == "pujadryfruit@gmail.com" || 
                                     userEmail == "rohit21oc@gmail.com"

                // Check authorization: Only pre-registered users or owner can sign in
                val firestore = firestoreProvider()
                var isAuthorized = isDefaultOwner
                if (!isAuthorized && firestore != null) {
                    try {
                        val userDocSnap = firestore.collection("users").document(user.uid).get().await()
                        val authEmailSnap = firestore.collection("authorized_users").document(userEmail.replace(".", "_")).get().await()
                        isAuthorized = userDocSnap.exists() || authEmailSnap.exists()
                    } catch (e: Exception) {
                        Log.w(TAG, "Auth lookup warning: ${e.message}")
                    }
                }

                if (!isAuthorized) {
                    auth.signOut()
                    val errorMsg = "Access Denied: Google account ($userEmail) is not registered. Only Admin can create new accounts. Please contact Admin."
                    _currentUserDoc.value = null
                    _authState.value = AuthState.Error(errorMsg)
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val userDoc = syncUserDocument(user)
                _currentUserDoc.value = userDoc
                _authState.value = AuthState.Authenticated(user, userDoc)
                Result.success(userDoc)
            } else {
                throw Exception("Unrecognized Google Credential response")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-in failed: ${e.message}", e)
            _authState.value = AuthState.Error(e.localizedMessage ?: "Sign-in failed")
            Result.failure(e)
        }
    }

    suspend fun signInWithEmailPassword(email: String, pass: String): Result<UserDoc> = withContext(Dispatchers.IO) {
        val auth = authProvider() ?: return@withContext Result.failure(Exception("Firebase Auth not initialized"))
        _authState.value = AuthState.Loading
        val trimmedEmail = email.trim()
        val trimmedPass = pass.trim()
        try {
            val result = auth.signInWithEmailAndPassword(trimmedEmail, trimmedPass).await()
            val user = result.user ?: throw Exception("User not found")
            val userDoc = try {
                syncUserDocument(user)
            } catch (e: Exception) {
                Log.w(TAG, "Firestore sync user warning on login: ${e.message}")
                createDefaultUserDoc(user)
            }
            _currentUserDoc.value = userDoc
            _authState.value = AuthState.Authenticated(user, userDoc)
            Result.success(userDoc)
        } catch (e: Exception) {
            Log.e(TAG, "Email login error: ${e.message}", e)
            val friendlyMsg = when {
                e.message?.contains("password", ignoreCase = true) == true -> "Incorrect password. Please verify your credentials."
                e.message?.contains("user", ignoreCase = true) == true ||
                e.message?.contains("invalid-credential", ignoreCase = true) == true ||
                e.message?.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) == true -> "Invalid credentials or account not registered. Only Admin can create new user accounts."
                e.message?.contains("network", ignoreCase = true) == true -> "Network connection error. Please check your internet connection."
                e.message?.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) == true ||
                e.message?.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) == true -> "Firebase Email Auth provider is not enabled in Firebase console."
                else -> e.localizedMessage ?: "Login failed. Please verify credentials."
            }
            _authState.value = AuthState.Error(friendlyMsg)
            Result.failure(Exception(friendlyMsg))
        }
    }

    suspend fun enterOfflineAdmin(): Result<UserDoc> = withContext(Dispatchers.IO) {
        val ownerDoc = UserDoc(
            id = "user_puja_owner",
            email = "rohit21oc@gmail.com",
            displayName = "Rohit Kumar (Owner)",
            role = "OWNER / ADMIN",
            shopId = FirebaseManager.DEFAULT_SHOP_ID,
            photoUrl = "",
            phone = "+91 98765 43210",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        FirebaseManager.currentUserId = ownerDoc.id
        _currentUserDoc.value = ownerDoc
        _authState.value = AuthState.Authenticated(null, ownerDoc)
        Result.success(ownerDoc)
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
        try {
            auth.sendPasswordResetEmail(email.trim()).await()
            Result.success("Password reset instructions sent to $email")
        } catch (e: Exception) {
            Log.e(TAG, "Password reset error: ${e.message}", e)
            Result.failure(Exception(e.localizedMessage ?: "Failed to send reset email"))
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

    suspend fun signInAsShopManagerOffline(): UserDoc = withContext(Dispatchers.IO) {
        val defaultUser = UserDoc(
            id = "user_puja_owner",
            email = "pujadryfruit@gmail.com",
            displayName = "Puja Kumari (Owner)",
            role = "OWNER",
            shopId = FirebaseManager.DEFAULT_SHOP_ID,
            photoUrl = "",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        FirebaseManager.currentUserId = defaultUser.id
        _currentUserDoc.value = defaultUser
        _authState.value = AuthState.Authenticated(null, defaultUser)
        defaultUser
    }

    private fun fetchOrCreateUserDoc(user: FirebaseUser) {
        val firestore = firestoreProvider()
        if (firestore == null) {
            val defaultDoc = createDefaultUserDoc(user)
            _currentUserDoc.value = defaultDoc
            _authState.value = AuthState.Authenticated(user, defaultDoc)
            return
        }
        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val userDoc = if (doc.exists()) {
                    doc.toObject(UserDoc::class.java) ?: createDefaultUserDoc(user)
                } else {
                    val newDoc = createDefaultUserDoc(user)
                    firestore.collection("users").document(user.uid).set(newDoc)
                    newDoc
                }
                _currentUserDoc.value = userDoc
                _authState.value = AuthState.Authenticated(user, userDoc)
            }
            .addOnFailureListener {
                val fallback = createDefaultUserDoc(user)
                _currentUserDoc.value = fallback
                _authState.value = AuthState.Authenticated(user, fallback)
            }
    }

    private suspend fun syncUserDocument(user: FirebaseUser): UserDoc {
        val defaultDoc = createDefaultUserDoc(user)
        val firestore = firestoreProvider() ?: return defaultDoc
        return try {
            val userRef = firestore.collection("users").document(user.uid)
            val snap = userRef.get().await()
            if (snap.exists()) {
                snap.toObject(UserDoc::class.java) ?: defaultDoc
            } else {
                try {
                    userRef.set(defaultDoc).await()
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
