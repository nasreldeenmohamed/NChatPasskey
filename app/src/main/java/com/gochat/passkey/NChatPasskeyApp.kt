package com.gochat.passkey

import android.app.Application
import com.gochat.passkey.data.CredentialStore
import com.gochat.passkey.data.FakeRelyingParty
import com.gochat.passkey.domain.AuthInteractor
import com.gochat.passkey.domain.PasskeyCeremonyGateway
import com.gochat.passkey.domain.RelyingParty

class NChatPasskeyApp : Application() {
    lateinit var credentialStore: CredentialStore
        private set
    lateinit var relyingParty: RelyingParty
        private set

    override fun onCreate() {
        super.onCreate()
        credentialStore = CredentialStore(this)
        relyingParty = FakeRelyingParty(credentialStore)
    }

    fun authInteractor(ceremony: PasskeyCeremonyGateway): AuthInteractor =
        AuthInteractor(relyingParty, ceremony)
}
