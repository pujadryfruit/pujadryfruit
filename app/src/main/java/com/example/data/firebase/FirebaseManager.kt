package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage

object FirebaseManager {
    private const val TAG = "FirebaseManager"
    const val DEFAULT_SHOP_ID = "puja_dryfruits_main"
    const val OAUTH_WEB_CLIENT_ID = "748428498825-sgcgcgifmqbvsbsih32i0qv75pnr1i7t.apps.googleusercontent.com"
    const val FIREBASE_PROJECT_ID = "puja-dryfruits"
    const val FIREBASE_STORAGE_BUCKET = "puja-dryfruits.firebasestorage.app"
    const val FIREBASE_APP_ID = "1:748428498825:android:23d86a5e757ef38c39b358"
    val FIREBASE_API_KEY: String = try {
        BuildConfig.FIREBASE_API_KEY.ifEmpty { "AIzaSyBxz7eYkFFkCOdr8tcmajh36DkS8E_biIw" }
    } catch (e: Throwable) {
        "AIzaSyBxz7eYkFFkCOdr8tcmajh36DkS8E_biIw"
    }

    private var _isInitialized = false
    val isInitialized: Boolean get() = _isInitialized

    var currentShopId: String = DEFAULT_SHOP_ID
    var currentUserId: String = "user_puja_owner"

    fun initialize(context: Context) {
        if (_isInitialized) return
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val app = try {
                    FirebaseApp.initializeApp(context)
                } catch (e: Throwable) {
                    null
                }
                if (app == null && FirebaseApp.getApps(context).isEmpty()) {
                    val options = FirebaseOptions.Builder()
                        .setApplicationId(FIREBASE_APP_ID)
                        .setProjectId(FIREBASE_PROJECT_ID)
                        .setApiKey(FIREBASE_API_KEY)
                        .setStorageBucket(FIREBASE_STORAGE_BUCKET)
                        .build()
                    FirebaseApp.initializeApp(context, options)
                }
            }
            _isInitialized = true
            try {
                currentUserId = auth?.currentUser?.uid ?: "user_puja_owner"
            } catch (e: Throwable) {
                currentUserId = "user_puja_owner"
            }
            Log.d(TAG, "Firebase initialized successfully. Shop: $currentShopId, User: $currentUserId")
        } catch (e: Throwable) {
            Log.e(TAG, "Firebase initialization warning: ${e.message}", e)
        }
    }

    val firestore: FirebaseFirestore?
        get() = try {
            val instance = FirebaseFirestore.getInstance()
            try {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                            .build()
                    )
                    .build()
                instance.firestoreSettings = settings
            } catch (e: Throwable) {
                // Settings already applied
            }
            instance
        } catch (e: Throwable) {
            Log.w(TAG, "Firestore not available: ${e.message}")
            null
        }

    val auth: FirebaseAuth?
        get() = try {
            FirebaseAuth.getInstance()
        } catch (e: Throwable) {
            Log.w(TAG, "FirebaseAuth not available: ${e.message}")
            null
        }

    val storage: FirebaseStorage?
        get() = try {
            FirebaseStorage.getInstance()
        } catch (e: Throwable) {
            Log.w(TAG, "FirebaseStorage not available: ${e.message}")
            null
        }

    val functions: FirebaseFunctions?
        get() = try {
            FirebaseFunctions.getInstance()
        } catch (e: Throwable) {
            Log.w(TAG, "FirebaseFunctions not available: ${e.message}")
            null
        }
}

