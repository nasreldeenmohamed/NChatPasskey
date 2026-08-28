package com.gochat.passkey.domain

class AuthInteractor(
    private val relyingParty: RelyingParty,
    private val ceremony: PasskeyCeremonyGateway,
) {
    fun requestOtp(msisdn: String) = relyingParty.requestOtp(msisdn)

    fun verifyOtp(msisdn: String, otp: String) = relyingParty.verifyOtp(msisdn, otp)

    suspend fun registerPasskey(enrollSession: EnrollSession): AuthResult<SessionTokens> {
        val start = when (val r = relyingParty.startPasskeyRegister(enrollSession)) {
            is AuthResult.Ok -> r.value
            is AuthResult.Err -> return r
        }
        val created = when (val r = ceremony.createPasskey(start.requestJson)) {
            is AuthResult.Ok -> r.value
            is AuthResult.Err -> return r
        }
        return relyingParty.finishPasskeyRegister(
            enrollSession = enrollSession,
            challengeId = start.challengeId,
            registrationResponseJson = created,
        )
    }

    suspend fun loginWithPasskey(): AuthResult<SessionTokens> {
        val start = when (val r = relyingParty.startPasskeyLogin()) {
            is AuthResult.Ok -> r.value
            is AuthResult.Err -> return r
        }
        val assertion = when (val r = ceremony.getPasskey(start.requestJson)) {
            is AuthResult.Ok -> r.value
            is AuthResult.Err -> return r
        }
        return relyingParty.finishPasskeyLogin(
            challengeId = start.challengeId,
            assertionResponseJson = assertion,
        )
    }

    fun hasRegisteredCredentials() = relyingParty.hasRegisteredCredentials()

    fun currentSession() = relyingParty.currentSession()

    fun signOut() = relyingParty.signOut()
}
