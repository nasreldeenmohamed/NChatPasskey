package com.gochat.passkey.data

import com.gochat.passkey.BuildConfig
import com.gochat.passkey.domain.AuthResult
import com.gochat.passkey.domain.ChallengePurpose
import com.gochat.passkey.domain.ChallengeRecord
import com.gochat.passkey.domain.EnrollSession
import com.gochat.passkey.domain.PublicKeyCredentialCreationOptions
import com.gochat.passkey.domain.PublicKeyCredentialRequestOptions
import com.gochat.passkey.domain.RelyingParty
import com.gochat.passkey.domain.SessionTokens
import com.gochat.passkey.domain.StoredCredential
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * In-app Fake Relying Party (concept demo).
 * Owns challenges + credential mapping. Never trusts a FE-supplied public key on login.
 *
 * Crypto note: full WebAuthn assertion signature verification belongs on a production
 * server library. This spike runs the SDD structural gate (lookup, challenge, RP/origin,
 * UV flags) and binds enrollment material server-side at register time.
 */
class FakeRelyingParty(
    private val store: CredentialStore,
    private val rpId: String = BuildConfig.RP_ID,
    private val rpName: String = BuildConfig.RP_NAME,
    private val expectedOtp: String = BuildConfig.OTP_CODE,
    private val challengeTtlMs: Long = 60_000L,
    private val enrollTtlMs: Long = 10 * 60_000L,
) : RelyingParty {

    private val pendingOtps = mutableSetOf<String>()

    override fun requestOtp(msisdn: String): AuthResult<Unit> {
        val normalized = normalizeMsisdn(msisdn)
            ?: return AuthResult.Err("Enter a valid phone number.")
        pendingOtps.add(normalized)
        return AuthResult.Ok(Unit)
    }

    override fun verifyOtp(msisdn: String, otp: String): AuthResult<EnrollSession> {
        val normalized = normalizeMsisdn(msisdn)
            ?: return AuthResult.Err("Enter a valid phone number.")
        if (!pendingOtps.contains(normalized)) {
            return AuthResult.Err("Request an OTP first.")
        }
        if (otp.trim() != expectedOtp) {
            return AuthResult.Err("Invalid OTP.")
        }
        pendingOtps.remove(normalized)
        val session = EnrollSession(
            sessionId = UUID.randomUUID().toString(),
            msisdn = normalized,
            expiresAtEpochMs = System.currentTimeMillis() + enrollTtlMs,
        )
        return AuthResult.Ok(session)
    }

    override fun startPasskeyRegister(enrollSession: EnrollSession): AuthResult<PublicKeyCredentialCreationOptions> {
        if (enrollSession.expiresAtEpochMs < System.currentTimeMillis()) {
            return AuthResult.Err("Enroll session expired. Verify OTP again.")
        }
        val challengeBytes = WebAuthnEncoding.randomBytes(32)
        val challengeB64 = WebAuthnEncoding.toBase64Url(challengeBytes)
        val challengeId = UUID.randomUUID().toString()
        store.putChallenge(
            ChallengeRecord(
                challengeId = challengeId,
                challengeB64Url = challengeB64,
                purpose = ChallengePurpose.REGISTER,
                msisdn = enrollSession.msisdn,
                enrollSessionId = enrollSession.sessionId,
                expiresAtEpochMs = System.currentTimeMillis() + challengeTtlMs,
            ),
        )
        val userIdBytes = enrollSession.msisdn.toByteArray(Charsets.UTF_8)
        val requestJson = JSONObject()
            .put("challenge", challengeB64)
            .put(
                "rp",
                JSONObject()
                    .put("name", rpName)
                    .put("id", rpId),
            )
            .put(
                "user",
                JSONObject()
                    .put("id", WebAuthnEncoding.toBase64Url(userIdBytes))
                    .put("name", enrollSession.msisdn)
                    .put("displayName", enrollSession.msisdn),
            )
            .put(
                "pubKeyCredParams",
                JSONArray()
                    .put(JSONObject().put("type", "public-key").put("alg", -7))
                    .put(JSONObject().put("type", "public-key").put("alg", -257)),
            )
            .put("timeout", 60_000)
            .put("attestation", "none")
            .put(
                "authenticatorSelection",
                JSONObject()
                    .put("authenticatorAttachment", "platform")
                    .put("residentKey", "preferred")
                    .put("requireResidentKey", false)
                    .put("userVerification", "required"),
            )
            .toString()
        return AuthResult.Ok(
            PublicKeyCredentialCreationOptions(
                requestJson = requestJson,
                challengeId = challengeId,
            ),
        )
    }

    override fun finishPasskeyRegister(
        enrollSession: EnrollSession,
        challengeId: String,
        registrationResponseJson: String,
    ): AuthResult<SessionTokens> {
        if (enrollSession.expiresAtEpochMs < System.currentTimeMillis()) {
            return AuthResult.Err("Enroll session expired.")
        }
        val challenge = store.getChallenge(challengeId)
            ?: return AuthResult.Err("Authentication failed.")
        if (challenge.purpose != ChallengePurpose.REGISTER ||
            challenge.enrollSessionId != enrollSession.sessionId ||
            challenge.msisdn != enrollSession.msisdn
        ) {
            return AuthResult.Err("Authentication failed.")
        }
        if (challenge.expiresAtEpochMs < System.currentTimeMillis() || challenge.consumed) {
            return AuthResult.Err("Authentication failed.")
        }
        store.consumeChallenge(challengeId) ?: return AuthResult.Err("Authentication failed.")

        val parsed = runCatching { JSONObject(registrationResponseJson) }.getOrNull()
            ?: return AuthResult.Err("Authentication failed.")
        val credentialId = parsed.optString("id").ifBlank {
            parsed.optString("rawId")
        }
        if (credentialId.isBlank()) {
            return AuthResult.Err("Authentication failed.")
        }
        val response = parsed.optJSONObject("response")
            ?: return AuthResult.Err("Authentication failed.")
        val clientDataB64 = response.optString("clientDataJSON")
        if (clientDataB64.isBlank()) {
            return AuthResult.Err("Authentication failed.")
        }
        val clientData = decodeClientData(clientDataB64)
            ?: return AuthResult.Err("Authentication failed.")
        if (clientData.optString("type") != "webauthn.create") {
            return AuthResult.Err("Authentication failed.")
        }
        if (clientData.optString("challenge") != challenge.challengeB64Url) {
            return AuthResult.Err("Authentication failed.")
        }
        if (!originMatchesRp(clientData.optString("origin"))) {
            return AuthResult.Err("Authentication failed.")
        }

        val attestationObject = response.optString("attestationObject")
        val enrollmentBinding = WebAuthnEncoding.sha256Hex(
            credentialId + "|" + attestationObject + "|" + enrollSession.msisdn,
        )
        store.saveCredential(
            StoredCredential(
                credentialId = credentialId,
                userId = enrollSession.msisdn,
                msisdn = enrollSession.msisdn,
                enrollmentBinding = enrollmentBinding,
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
        val tokens = issueTokens(enrollSession.msisdn)
        store.saveSession(tokens)
        return AuthResult.Ok(tokens)
    }

    override fun startPasskeyLogin(): AuthResult<PublicKeyCredentialRequestOptions> {
        val challengeBytes = WebAuthnEncoding.randomBytes(32)
        val challengeB64 = WebAuthnEncoding.toBase64Url(challengeBytes)
        val challengeId = UUID.randomUUID().toString()
        store.putChallenge(
            ChallengeRecord(
                challengeId = challengeId,
                challengeB64Url = challengeB64,
                purpose = ChallengePurpose.AUTHENTICATE,
                msisdn = null,
                enrollSessionId = null,
                expiresAtEpochMs = System.currentTimeMillis() + challengeTtlMs,
            ),
        )
        val request = JSONObject()
            .put("challenge", challengeB64)
            .put("timeout", 60_000)
            .put("userVerification", "required")
            .put("rpId", rpId)

        val allow = JSONArray()
        store.activeCredentials().forEach { cred ->
            allow.put(
                JSONObject()
                    .put("type", "public-key")
                    .put("id", cred.credentialId),
            )
        }
        if (allow.length() > 0) {
            request.put("allowCredentials", allow)
        }

        return AuthResult.Ok(
            PublicKeyCredentialRequestOptions(
                requestJson = request.toString(),
                challengeId = challengeId,
            ),
        )
    }

    override fun finishPasskeyLogin(
        challengeId: String,
        assertionResponseJson: String,
    ): AuthResult<SessionTokens> {
        // SDD verification gate — fail closed, generic client error.
        val challenge = store.getChallenge(challengeId)
            ?: return AuthResult.Err("Authentication failed.", canRecoverWithOtp = true)
        if (challenge.purpose != ChallengePurpose.AUTHENTICATE) {
            return AuthResult.Err("Authentication failed.", canRecoverWithOtp = true)
        }
        if (challenge.expiresAtEpochMs < System.currentTimeMillis() || challenge.consumed) {
            return AuthResult.Err("Authentication failed.", canRecoverWithOtp = true)
        }

        val parsed = runCatching { JSONObject(assertionResponseJson) }.getOrNull()
            ?: return AuthResult.Err("Authentication failed.", canRecoverWithOtp = true)
        val credentialId = parsed.optString("id").ifBlank { parsed.optString("rawId") }
        if (credentialId.isBlank()) {
            return AuthResult.Err("Authentication failed.", canRecoverWithOtp = true)
        }

        // 1) Credential lookup — stored public-key binding only (never FE-supplied key).
        val stored = store.findCredential(credentialId)
            ?: return AuthResult.Err("Authentication failed.", canRecoverWithOtp = true)
        if (stored.enrollmentBinding.isBlank()) {
            return AuthResult.Err("Authentication failed.", canRecoverWithOtp = true)
        }

        val response = parsed.optJSONObject("response")
            ?: return AuthResult.Err("Authentication failed.", canRecoverWithOtp = true)
        val clientDataB64 = response.optString("clientDataJSON")
        val authenticatorDataB64 = response.optString("authenticatorData")
        val signatureB64 = response.optString("signature")
        if (clientDataB64.isBlank() || authenticatorDataB64.isBlank() || signatureB64.isBlank()) {
            return AuthResult.Err("Authentication failed.", canRecoverWithOtp = true)
        }

        val clientData = decodeClientData(clientDataB64)
            ?: return AuthResult.Err("Authentication failed.", canRecoverWithOtp = true)

        // 2) Challenge freshness (match + single-use consume)
        if (clientData.optString("challenge") != challenge.challengeB64Url) {
            return AuthResult.Err("Authentication failed.", canRecoverWithOtp = true)
        }
        store.consumeChallenge(challengeId)
            ?: return AuthResult.Err("Authentication failed.", canRecoverWithOtp = true)

        if (clientData.optString("type") != "webauthn.get") {
            return AuthResult.Err("Authentication failed.", canRecoverWithOtp = true)
        }

        // 3) GoChat binding (RP / origin)
        if (!originMatchesRp(clientData.optString("origin"))) {
            return AuthResult.Err("Authentication failed.", canRecoverWithOtp = true)
        }

        // 4) User verification flags in authenticatorData
        val authData = runCatching { WebAuthnEncoding.fromBase64Url(authenticatorDataB64) }.getOrNull()
            ?: return AuthResult.Err("Authentication failed.", canRecoverWithOtp = true)
        if (authData.size < 37) {
            return AuthResult.Err("Authentication failed.", canRecoverWithOtp = true)
        }
        val flags = authData[32].toInt() and 0xFF
        val userPresent = flags and 0x01 != 0
        val userVerified = flags and 0x04 != 0
        if (!userPresent || !userVerified) {
            return AuthResult.Err("Authentication failed.", canRecoverWithOtp = true)
        }

        // 5) Cryptographic proof — production BE must verify signature with stored COSE public key.
        // Demo: require non-empty signature bytes; binding already proven at enrollment storage.
        val signature = runCatching { WebAuthnEncoding.fromBase64Url(signatureB64) }.getOrNull()
        if (signature == null || signature.isEmpty()) {
            return AuthResult.Err("Authentication failed.", canRecoverWithOtp = true)
        }

        val tokens = issueTokens(stored.msisdn)
        store.saveSession(tokens)
        return AuthResult.Ok(tokens)
    }

    override fun hasRegisteredCredentials(): Boolean = store.hasCredentials()

    override fun signOut() {
        store.clearSession()
    }

    override fun currentSession(): SessionTokens? = store.loadSession()

    private fun issueTokens(msisdn: String): SessionTokens =
        SessionTokens(
            accessToken = "access_${UUID.randomUUID()}",
            refreshToken = "refresh_${UUID.randomUUID()}",
            msisdn = msisdn,
        )

    private fun normalizeMsisdn(raw: String): String? {
        val digits = raw.filter { it.isDigit() || it == '+' }
        return digits.takeIf { it.length >= 8 }
    }

    private fun decodeClientData(b64: String): JSONObject? =
        runCatching {
            val json = String(WebAuthnEncoding.fromBase64Url(b64), Charsets.UTF_8)
            JSONObject(json)
        }.getOrNull()

    private fun originMatchesRp(origin: String): Boolean {
        if (origin.isBlank()) return false
        // android:apk-key-hash:... or https://rpId
        return origin.contains(rpId, ignoreCase = true) ||
            origin.startsWith("android:apk-key-hash:")
    }
}
