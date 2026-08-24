package com.example.data.firebase

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class CloudFinancialSummary(
    val totalSales: Double = 0.0,
    val totalCost: Double = 0.0,
    val totalGrossProfit: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val netProfit: Double = 0.0,
    val totalOrders: Int = 0
)

class FirebaseCloudFunctionsClient(
    private val functionsProvider: () -> FirebaseFunctions? = { FirebaseManager.functions }
) {
    private val TAG = "CloudFunctionsClient"

    suspend fun fetchFinancialSummary(
        shopId: String,
        startDate: Long,
        endDate: Long
    ): Result<CloudFinancialSummary> = withContext(Dispatchers.IO) {
        val functions = functionsProvider() ?: return@withContext Result.failure(Exception("Functions client not available"))
        try {
            val payload = hashMapOf(
                "shopId" to shopId,
                "startDate" to startDate,
                "endDate" to endDate
            )
            val result = functions
                .getHttpsCallable("getShopFinancialSummary")
                .call(payload)
                .await()

            val data = result.data as? Map<*, *>
            if (data != null) {
                val summary = CloudFinancialSummary(
                    totalSales = (data["totalSales"] as? Number)?.toDouble() ?: 0.0,
                    totalCost = (data["totalCost"] as? Number)?.toDouble() ?: 0.0,
                    totalGrossProfit = (data["totalGrossProfit"] as? Number)?.toDouble() ?: 0.0,
                    totalExpenses = (data["totalExpenses"] as? Number)?.toDouble() ?: 0.0,
                    netProfit = (data["netProfit"] as? Number)?.toDouble() ?: 0.0,
                    totalOrders = (data["totalOrders"] as? Number)?.toInt() ?: 0
                )
                Result.success(summary)
            } else {
                Result.failure(Exception("Invalid cloud function return structure"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cloud function invocation fallback: ${e.message}")
            Result.failure(e)
        }
    }
}
