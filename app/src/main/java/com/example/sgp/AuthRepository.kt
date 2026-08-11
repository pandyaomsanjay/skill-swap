package com.example.sgp.api

import com.example.sgp.SupabaseClient
import com.example.sgp.User
import com.google.firebase.firestore.FirebaseFirestore
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable

@Serializable
data class ForgotPasswordResponse(val message: String)

@Serializable
data class VerifyOtpResponse(
    val success: Boolean,
    val message: String,
    val lockedUntil: Long? = null // Supabase manages its own rate limiting; kept for UI compatibility
)

@Serializable
data class ResendOtpResponse(val message: String)

@Serializable
data class ResetPasswordResponse(val success: Boolean, val message: String)

@Serializable
data class LoginResponse(
    val success: Boolean,
    val message: String,
    val email: String? = null
)

object AuthRepository {

    private const val TAG = "AuthRepository"
    private const val GENERIC_SENT_MESSAGE =
        "If an account exists for this email, a code has been sent."

    suspend fun getLoginProvider(email: String): String? {
        return try {
            val snapshot = FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .await()

            val provider = snapshot.documents.firstOrNull()
                ?.toObject(User::class.java)
                ?.loginProvider

            android.util.Log.d(TAG, "getLoginProvider($email) -> $provider")
            provider
        } catch (e: Exception) {
            android.util.Log.e(TAG, "getLoginProvider failed: ${e.message}", e)
            null
        }
    }

    /**
     * Email/password login via Supabase Auth. This is now the single source
     * of truth for password checks — Firebase Auth is no longer used for
     * email/password sign-in, only for Google Sign-In and Firestore access.
     */
    suspend fun loginWithPassword(email: String, password: String): LoginResponse {
        return try {
            SupabaseClient.client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val sessionEmail = SupabaseClient.client.auth.currentSessionOrNull()?.user?.email
            LoginResponse(success = true, message = "Login successful", email = sessionEmail ?: email)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "loginWithPassword failed: ${e.message}", e)
            LoginResponse(success = false, message = "Invalid email or password.")
        }
    }

    suspend fun forgotPassword(email: String): ForgotPasswordResponse {
        return try {
            SupabaseClient.client.auth.resetPasswordForEmail(email)
            ForgotPasswordResponse(GENERIC_SENT_MESSAGE)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "forgotPassword failed: ${e.message}", e)
            // Never leak whether the email exists — same message either way
            ForgotPasswordResponse(GENERIC_SENT_MESSAGE)
        }
    }

    suspend fun verifyOtp(email: String, otp: String): VerifyOtpResponse {
        return try {
            SupabaseClient.client.auth.verifyEmailOtp(
                type = OtpType.Email.RECOVERY,
                email = email,
                token = otp
            )

            val session = SupabaseClient.client.auth.currentSessionOrNull()
            android.util.Log.d(
                TAG,
                "After verifyEmailOtp -> session user=${session?.user?.email}, " +
                        "expiresAt=${session?.expiresAt}"
            )

            VerifyOtpResponse(success = true, message = "OTP verified")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "verifyOtp failed: ${e.message}", e)
            VerifyOtpResponse(
                success = false,
                message = e.message ?: "Invalid or expired OTP. Please try again."
            )
        }
    }

    suspend fun resendOtp(email: String): ResendOtpResponse {
        return try {
            SupabaseClient.client.auth.resetPasswordForEmail(email)
            ResendOtpResponse("A new code has been sent.")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "resendOtp failed: ${e.message}", e)
            ResendOtpResponse("Couldn't resend code. Please try again.")
        }
    }

    suspend fun resetPassword(newPassword: String): ResetPasswordResponse {
        return try {
            val sessionBefore = SupabaseClient.client.auth.currentSessionOrNull()
            android.util.Log.d(
                TAG,
                "resetPassword START -> session user=${sessionBefore?.user?.email}, " +
                        "expiresAt=${sessionBefore?.expiresAt}"
            )

            if (sessionBefore == null) {
                android.util.Log.e(TAG, "resetPassword: NO ACTIVE SESSION — updateUser will fail or target the wrong account")
            }

            SupabaseClient.client.auth.updateUser {
                password = newPassword
            }

            val sessionAfter = SupabaseClient.client.auth.currentSessionOrNull()
            android.util.Log.d(
                TAG,
                "resetPassword SUCCESS -> session user=${sessionAfter?.user?.email}"
            )

            ResetPasswordResponse(success = true, message = "Password updated successfully.")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "resetPassword failed: ${e.message}", e)
            ResetPasswordResponse(
                success = false,
                message = e.message ?: "Couldn't reset password. Please try again."
            )
        }
    }
}