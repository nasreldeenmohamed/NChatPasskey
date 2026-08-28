package com.gochat.passkey.domain

sealed class AuthResult<out T> {
    data class Ok<T>(val value: T) : AuthResult<T>()
    data class Err(val message: String, val canRecoverWithOtp: Boolean = false) : AuthResult<Nothing>()
}

data class SessionTokens(
    val accessToken: String,
    val refreshToken: String,
    val msisdn: String,
)

data class EnrollSession(
    val sessionId: String,
    val msisdn: String,
    val expiresAtEpochMs: Long,
)

data class PublicKeyCredentialCreationOptions(
    val requestJson: String,
    val challengeId: String,
)

data class PublicKeyCredentialRequestOptions(
    val requestJson: String,
    val challengeId: String,
)

data class StoredCredential(
    val credentialId: String,
    val userId: String,
    val msisdn: String,
    /** Opaque enrollment binding — never a client-supplied login public key. */
    val enrollmentBinding: String,
    val createdAtEpochMs: Long,
    val revoked: Boolean = false,
)

data class ChallengeRecord(
    val challengeId: String,
    val challengeB64Url: String,
    val purpose: ChallengePurpose,
    val msisdn: String?,
    val enrollSessionId: String?,
    val expiresAtEpochMs: Long,
    val consumed: Boolean = false,
)

enum class ChallengePurpose {
    REGISTER,
    AUTHENTICATE,
}
