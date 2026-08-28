package com.gochat.passkey.data

import android.app.Activity
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.gochat.passkey.domain.AuthResult
import com.gochat.passkey.domain.PasskeyCeremonyGateway

class CredentialManagerPasskeyGateway(
    private val activity: Activity,
) : PasskeyCeremonyGateway {

    private val credentialManager = CredentialManager.create(activity)

    override suspend fun createPasskey(requestJson: String): AuthResult<String> {
        return try {
            val request = CreatePublicKeyCredentialRequest(requestJson)
            val result = credentialManager.createCredential(
                context = activity,
                request = request,
            )
            val response = result as? CreatePublicKeyCredentialResponse
                ?: return AuthResult.Err("Unexpected create credential response.")
            AuthResult.Ok(response.registrationResponseJson)
        } catch (_: CreateCredentialCancellationException) {
            AuthResult.Err("Passkey creation cancelled.")
        } catch (e: CreateCredentialException) {
            AuthResult.Err(
                "Passkey creation failed: ${e.type}. " +
                    "If Asset Links / RP ID are not configured, Credential Manager will reject the request.",
            )
        } catch (e: Exception) {
            AuthResult.Err(e.message ?: "Passkey creation failed.")
        }
    }

    override suspend fun getPasskey(requestJson: String): AuthResult<String> {
        return try {
            val option = GetPublicKeyCredentialOption(requestJson)
            val request = GetCredentialRequest(listOf(option))
            val result = credentialManager.getCredential(
                context = activity,
                request = request,
            )
            val credential = result.credential as? PublicKeyCredential
                ?: return AuthResult.Err(
                    "Unexpected credential type.",
                    canRecoverWithOtp = true,
                )
            AuthResult.Ok(credential.authenticationResponseJson)
        } catch (_: GetCredentialCancellationException) {
            AuthResult.Err("Sign-in cancelled.", canRecoverWithOtp = true)
        } catch (_: NoCredentialException) {
            AuthResult.Err("No passkey found for this app.", canRecoverWithOtp = true)
        } catch (e: GetCredentialException) {
            AuthResult.Err(
                "Passkey sign-in failed: ${e.type}",
                canRecoverWithOtp = true,
            )
        } catch (e: Exception) {
            AuthResult.Err(e.message ?: "Passkey sign-in failed.", canRecoverWithOtp = true)
        }
    }
}
