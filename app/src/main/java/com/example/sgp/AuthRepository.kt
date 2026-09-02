package com.example.sgp.api

import com.example.sgp.SupabaseClient
import com.example.sgp.User
import com.google.firebase.firestore.FirebaseFirestore
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class ForgotPasswordResponse(val message: String)

@Serializable
data class VerifyOtpResponse(
    val success: Boolean,
    val message: String,
    val lockedUntil: Long? = null
)

@Serializable
data class ResendOtpResponse(val message: String)

@Serializable
data class ResetPasswordResponse(val success: Boolean, val message: String)

@Serializable
data class LoginResponse(
    val success: Boolean,
    val message: String,
    val email: String? = null,
    val isLocked: Boolean = false,
    val secondsRemaining: Int = 0,
    val attemptsRemaining: Int = 5
)

@Serializable
data class LoginLockStatus(
    @SerialName("is_locked") val isLocked: Boolean,
    @SerialName("seconds_remaining") val secondsRemaining: Int,
    @SerialName("attempts_remaining") val attemptsRemaining: Int
)

object AuthRepository {

    private const val TAG = "AuthRepository"
    private const val GENERIC_SENT_MESSAGE =
        "If an account exists for this email, a code has been sent."
    private const val MAX_ATTEMPTS = 5

    /**
     * Single source of truth for email normalization across this repository.
     * Every function here that takes an email runs it through this first,
     * so a Firestore query written with one casing can never fail to match
     * a Supabase call made with another.
     */
    private fun normalize(email: String): String = email.trim().lowercase()

    suspend fun getLoginProvider(email: String): String? {
        val normalizedEmail = normalize(email)
        return try {
            val snapshot = FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("email", normalizedEmail)
                .limit(1)
                .get()
                .await()

            val provider = snapshot.documents.firstOrNull()
                ?.toObject(User::class.java)
                ?.loginProvider

            android.util.Log.d(TAG, "getLoginProvider($normalizedEmail) -> $provider")
            provider
        } catch (e: Exception) {
            android.util.Log.e(TAG, "getLoginProvider failed: ${e.message}", e)
            null
        }
    }

    // ---------- Rate limiting (server-side, enforced via Postgres RPCs) ----------

    private suspend fun checkLoginLock(email: String): LoginLockStatus {
        return try {
            SupabaseClient.client.postgrest.rpc(
                "check_login_lock",
                buildJsonObject { put("p_email", email) }
            ).decodeAs<List<LoginLockStatus>>().firstOrNull()
                ?: LoginLockStatus(false, 0, MAX_ATTEMPTS)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "checkLoginLock failed: ${e.message}", e)
            // Fail-open here so a transient Supabase hiccup doesn't lock everyone
            // out of the app — record_failed_login below still enforces the real limit.
            LoginLockStatus(false, 0, MAX_ATTEMPTS)
        }
    }

    private suspend fun recordFailedLogin(email: String): LoginLockStatus {
        return try {
            SupabaseClient.client.postgrest.rpc(
                "record_failed_login",
                buildJsonObject { put("p_email", email) }
            ).decodeAs<List<LoginLockStatus>>().firstOrNull()
                ?: LoginLockStatus(false, 0, MAX_ATTEMPTS)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "recordFailedLogin failed: ${e.message}", e)
            LoginLockStatus(false, 0, MAX_ATTEMPTS)
        }
    }

    private suspend fun resetLoginAttempts(email: String) {
        try {
            SupabaseClient.client.postgrest.rpc(
                "reset_login_attempts",
                buildJsonObject { put("p_email", email) }
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "resetLoginAttempts failed: ${e.message}", e)
        }
    }

    private fun lockedMessage(secondsRemaining: Int): String {
        val minutes = (secondsRemaining + 59) / 60
        return "Too many failed attempts. Try again in $minutes minute${if (minutes == 1) "" else "s"}."
    }

    // ---------- Dictionary / weak-password check (Postgres RPC) ----------

    /**
     * Checks a candidate password against the server-side is_password_common
     * RPC (exact common-password matches, repeated-character strings,
     * repeated chunks, keyboard walks, and leetspeak-normalized matches).
     * Call this BEFORE creating or updating a password, alongside the
     * existing client-side complexity rules.
     */
    suspend fun isPasswordCommon(password: String): Boolean {
        return try {
            SupabaseClient.client.postgrest.rpc(
                "is_password_common",
                buildJsonObject { put("p_password", password) }
            ).decodeAs<Boolean>()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "isPasswordCommon failed: ${e.message}", e)
            false // fail-open so a network hiccup doesn't block signup entirely
        }
    }

    /**
     * Email/password login via Supabase Auth, gated by a server-side
     * rate limit (5 failed attempts -> 15 minute lock), enforced in Postgres
     * via SECURITY DEFINER RPCs so it can't be bypassed from the client.
     */
    suspend fun loginWithPassword(email: String, password: String): LoginResponse {
        val normalizedEmail = normalize(email)

        val lockStatus = checkLoginLock(normalizedEmail)
        if (lockStatus.isLocked) {
            return LoginResponse(
                success = false,
                message = lockedMessage(lockStatus.secondsRemaining),
                isLocked = true,
                secondsRemaining = lockStatus.secondsRemaining
            )
        }

        return try {
            SupabaseClient.client.auth.signInWith(Email) {
                this.email = normalizedEmail
                this.password = password
            }
            resetLoginAttempts(normalizedEmail)
            val sessionEmail = SupabaseClient.client.auth.currentSessionOrNull()?.user?.email
            LoginResponse(success = true, message = "Login successful", email = sessionEmail ?: normalizedEmail)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "loginWithPassword failed: ${e.message}", e)
            val record = recordFailedLogin(normalizedEmail)
            if (record.isLocked) {
                LoginResponse(
                    success = false,
                    message = lockedMessage(record.secondsRemaining),
                    isLocked = true,
                    secondsRemaining = record.secondsRemaining
                )
            } else {
                LoginResponse(
                    success = false,
                    message = "Invalid email or password. ${record.attemptsRemaining} attempt${if (record.attemptsRemaining == 1) "" else "s"} remaining.",
                    attemptsRemaining = record.attemptsRemaining
                )
            }
        }
    }

    suspend fun forgotPassword(email: String): ForgotPasswordResponse {
        val normalizedEmail = normalize(email)
        return try {
            SupabaseClient.client.auth.resetPasswordForEmail(normalizedEmail)
            ForgotPasswordResponse(GENERIC_SENT_MESSAGE)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "forgotPassword failed: ${e.message}", e)
            ForgotPasswordResponse(GENERIC_SENT_MESSAGE)
        }
    }

    suspend fun verifyOtp(email: String, otp: String): VerifyOtpResponse {
        val normalizedEmail = normalize(email)
        return try {
            SupabaseClient.client.auth.verifyEmailOtp(
                type = OtpType.Email.RECOVERY,
                email = normalizedEmail,
                token = otp
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
        val normalizedEmail = normalize(email)
        return try {
            SupabaseClient.client.auth.resetPasswordForEmail(normalizedEmail)
            ResendOtpResponse("A new code has been sent.")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "resendOtp failed: ${e.message}", e)
            ResendOtpResponse("Couldn't resend code. Please try again.")
        }
    }

    suspend fun resetPassword(newPassword: String): ResetPasswordResponse {
        if (isPasswordCommon(newPassword)) {
            return ResetPasswordResponse(
                success = false,
                message = "This password is too common or predictable. Please choose a stronger one."
            )
        }
        return try {
            SupabaseClient.client.auth.updateUser {
                password = newPassword
            }
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