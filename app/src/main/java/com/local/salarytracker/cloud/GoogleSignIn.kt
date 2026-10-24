package com.local.salarytracker.cloud

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.local.salarytracker.BuildConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

/** A sign-in problem with a message that is safe to show as it is. */
class SignInException(message: String) : Exception(message)

data class SignedIn(val userId: String, val name: String, val email: String)

object GoogleSignIn {
    const val NOT_SET_UP = "Google sign-in is not set up yet"

    /**
     * Opens the Google account chooser, then signs in to Supabase with the Google ID token.
     * [activityContext] must be an Activity (Credential Manager shows a bottom sheet).
     */
    suspend fun signIn(activityContext: Context): SignedIn {
        if (!CloudConfig.isConfigured) throw SignInException(NOT_SET_UP)

        // Google puts the SHA-256 of the nonce inside the token; Supabase wants the raw nonce to compare.
        val rawNonce = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val hashedNonce = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray()).joinToString("") { "%02x".format(it) }

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(true)
            .setNonce(hashedNonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        val credential = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            throw SignInException("Sign-in was cancelled")
        } catch (e: NoCredentialException) {
            throw SignInException("No Google account on this phone. Add one in your phone settings, then try again.")
        } catch (e: GetCredentialException) {
            // Error type and message only (never tokens), so setup problems can be told apart in logcat.
            android.util.Log.w("SalaryTracker", "Google sign-in failed: ${e.type}: ${e.message}")
            val text = (e.message.orEmpty() + " " + e.type).lowercase()
            throw SignInException(
                when {
                    "developer" in text || "10:" in text || "16:" in text || "audience" in text -> NOT_SET_UP
                    "network" in text || "connect" in text -> "No internet. Check your connection and try again."
                    else -> "Could not sign in with Google. Please try again."
                },
            )
        }

        val google = if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                GoogleIdTokenCredential.createFrom(credential.data)
            } catch (e: Exception) {
                throw SignInException("Could not sign in with Google. Please try again.")
            }
        } else {
            throw SignInException("Could not sign in with Google. Please try again.")
        }

        try {
            val auth = Cloud.client.auth
            auth.signInWith(IDToken) {
                idToken = google.idToken
                provider = Google
                nonce = rawNonce
            }
            val user = auth.currentUserOrNull() ?: throw SignInException("Could not sign in. Please try again.")
            return SignedIn(user.id, google.displayName.orEmpty(), user.email ?: google.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SignInException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("SalaryTracker", "Supabase sign-in failed: ${e.javaClass.simpleName}: ${e.message?.take(300)}")
            throw SignInException(
                when {
                    generateSequence<Throwable>(e) { it.cause }.take(8).any { it is IOException } ->
                        "No internet. Check your connection and try again."
                    e.message.orEmpty().contains("provider", ignoreCase = true) && e.message.orEmpty().contains("enabled", ignoreCase = true) -> NOT_SET_UP
                    else -> "Could not sign in. Please try again."
                },
            )
        }
    }
}
