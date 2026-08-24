package com.example.data.firebase

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Handles Firebase Storage operations for Puja Dry Fruits:
 * - Product catalog images (compressed JPEG)
 * - Expense receipts & bills
 * - Shop logo & storefront banner
 */
class FirebaseStorageService(
    private val storageProvider: () -> FirebaseStorage? = { FirebaseManager.storage }
) {
    private val TAG = "FirebaseStorageService"

    suspend fun uploadProductImage(
        shopId: String,
        productId: String,
        bitmap: Bitmap
    ): Result<String> = withContext(Dispatchers.IO) {
        val storage = storageProvider() ?: return@withContext Result.failure(Exception("Firebase Storage not available"))
        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val data = stream.toByteArray()

            val path = "shops/$shopId/products/$productId.jpg"
            val ref = storage.reference.child(path)

            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .setCustomMetadata("shopId", shopId)
                .setCustomMetadata("productId", productId)
                .build()

            ref.putBytes(data, metadata).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Log.d(TAG, "Product image uploaded successfully: $downloadUrl")
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload product image: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun uploadReceiptImage(
        shopId: String,
        expenseId: String,
        bitmap: Bitmap
    ): Result<String> = withContext(Dispatchers.IO) {
        val storage = storageProvider() ?: return@withContext Result.failure(Exception("Firebase Storage not available"))
        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val data = stream.toByteArray()

            val path = "shops/$shopId/receipts/$expenseId.jpg"
            val ref = storage.reference.child(path)

            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .setCustomMetadata("shopId", shopId)
                .setCustomMetadata("expenseId", expenseId)
                .build()

            ref.putBytes(data, metadata).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload receipt image: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun uploadShopLogo(
        shopId: String,
        bitmap: Bitmap
    ): Result<String> = withContext(Dispatchers.IO) {
        val storage = storageProvider() ?: return@withContext Result.failure(Exception("Firebase Storage not available"))
        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, stream)
            val data = stream.toByteArray()

            val path = "shops/$shopId/branding/logo.png"
            val ref = storage.reference.child(path)
            ref.putBytes(data).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload shop logo: ${e.message}", e)
            Result.failure(e)
        }
    }
}
