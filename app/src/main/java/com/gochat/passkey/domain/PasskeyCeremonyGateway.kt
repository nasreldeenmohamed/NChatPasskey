package com.gochat.passkey.domain

interface PasskeyCeremonyGateway {
    suspend fun createPasskey(requestJson: String): AuthResult<String>
    suspend fun getPasskey(requestJson: String): AuthResult<String>
}
