package com.gochat.passkey.domain

interface RelyingParty {
    fun requestOtp(msisdn: String): AuthResult<Unit>
    fun verifyOtp(msisdn: String, otp: String): AuthResult<EnrollSession>
    fun startPasskeyRegister(enrollSession: EnrollSession): AuthResult<PublicKeyCredentialCreationOptions>
    fun finishPasskeyRegister(
        enrollSession: EnrollSession,
        challengeId: String,
        registrationResponseJson: String,
    ): AuthResult<SessionTokens>
    fun startPasskeyLogin(): AuthResult<PublicKeyCredentialRequestOptions>
    fun finishPasskeyLogin(
        challengeId: String,
        assertionResponseJson: String,
    ): AuthResult<SessionTokens>
    fun hasRegisteredCredentials(): Boolean
    fun signOut()
    fun currentSession(): SessionTokens?
}
