package com.example.sgp

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Result data class for credit purchase operations.
 */
data class PurchaseResult(
    val success: Boolean,
    val transactionId: String? = null,
    val packageId: String? = null,
    val packageName: String? = null,
    val creditsAdded: Int = 0,
    val newBalance: Int? = null,
    val priceInRupees: Int = 0,
    val errorMessage: String? = null,
    val isDemo: Boolean = false
)

/**
 * Clean payment abstraction layer that separates checkout presentation from payment execution.
 * Allows easy switching between Simulated Demo Payments and real Razorpay payments.
 */
interface PaymentGatewayService {
    suspend fun executePayment(
        context: Context,
        packageId: String,
        paymentMethod: String,
        clientTransactionId: String? = null
    ): PurchaseResult
}

/**
 * Simulated Demo Payment Gateway implementation.
 * Calls the Supabase Edge Function 'simulate-credit-purchase'.
 * Strictly guarded against production/release execution.
 */
class DemoPaymentGatewayService(
    private val edgeFunctionsBaseUrl: String = "https://ghrxltlstncjcizyyqfo.supabase.co/functions/v1"
) : PaymentGatewayService {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun executePayment(
        context: Context,
        packageId: String,
        paymentMethod: String,
        clientTransactionId: String?
    ): PurchaseResult = withContext(Dispatchers.IO) {
        // Enforce safety guard: demo gateway only allowed in debug/non-release builds
        val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) {
            return@withContext PurchaseResult(
                success = false,
                errorMessage = "Demo payment gateway is strictly disabled in release builds."
            )
        }

        val token = resolveActiveToken()
            ?: return@withContext PurchaseResult(
                success = false,
                errorMessage = "Authentication session expired. Please sign in again."
            )

        try {
            val jsonPayload = JSONObject().apply {
                put("packageId", packageId)
                put("paymentMethod", paymentMethod)
                if (!clientTransactionId.isNullOrBlank()) {
                    put("clientTransactionId", clientTransactionId)
                }
            }.toString()

            val endpointUrl = "$edgeFunctionsBaseUrl/simulate-credit-purchase"
            android.util.Log.d("DemoPayment", "Initiating simulated purchase POST to: $endpointUrl (packageId=$packageId, paymentMethod=$paymentMethod)")

            val request = Request.Builder()
                .url(endpointUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                android.util.Log.d("DemoPayment", "Simulated purchase response: HTTP ${response.code}, body: ${bodyStr.take(300)}")

                if (!response.isSuccessful) {
                    val errMsg = try {
                        val parsed = JSONObject(bodyStr).optString("error")
                        if (!parsed.isNullOrBlank()) parsed else "Server returned HTTP ${response.code}"
                    } catch (_: Exception) {
                        "Server returned HTTP ${response.code}"
                    }
                    return@withContext PurchaseResult(
                        success = false,
                        errorMessage = errMsg
                    )
                }

                val json = JSONObject(bodyStr)
                PurchaseResult(
                    success = json.optBoolean("success", false),
                    transactionId = json.optString("transactionId"),
                    packageId = json.optString("packageId"),
                    packageName = json.optString("packageName"),
                    creditsAdded = json.optInt("creditsAdded", 0),
                    newBalance = if (json.has("newBalance") && !json.isNull("newBalance")) json.getInt("newBalance") else null,
                    priceInRupees = json.optInt("priceInRupees", 0),
                    isDemo = json.optBoolean("isDemo", true)
                )
            }
        } catch (e: Exception) {
            PurchaseResult(
                success = false,
                errorMessage = e.localizedMessage ?: "Network error during demo payment."
            )
        }
    }

    private suspend fun resolveActiveToken(): String? {
        val fbUser = FirebaseAuth.getInstance().currentUser
        if (fbUser != null) {
            try {
                val tokenResult = fbUser.getIdToken(false).await()
                if (!tokenResult.token.isNullOrBlank()) {
                    return tokenResult.token
                }
            } catch (_: Exception) {}
        }

        try {
            val supaToken = SupabaseClient.client.auth.currentAccessTokenOrNull()
            if (!supaToken.isNullOrBlank()) {
                return supaToken
            }
        } catch (_: Exception) {}

        return null
    }
}
