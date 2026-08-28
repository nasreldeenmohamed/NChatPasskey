package com.gochat.passkey.data

import android.content.Context
import android.content.SharedPreferences
import com.gochat.passkey.domain.ChallengeRecord
import com.gochat.passkey.domain.SessionTokens
import com.gochat.passkey.domain.StoredCredential
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable store so Flow B survives process death. Challenges stay in-memory (short TTL).
 */
class CredentialStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("nchat_passkey_rp", Context.MODE_PRIVATE)

    @Volatile
    private var challenges: Map<String, ChallengeRecord> = emptyMap()

    fun saveCredential(credential: StoredCredential) {
        val all = loadCredentials().toMutableList()
        all.removeAll { it.credentialId == credential.credentialId }
        all.add(credential)
        prefs.edit().putString(KEY_CREDENTIALS, serializeCredentials(all)).apply()
    }

    fun findCredential(credentialId: String): StoredCredential? =
        loadCredentials().firstOrNull { it.credentialId == credentialId && !it.revoked }

    fun activeCredentials(): List<StoredCredential> =
        loadCredentials().filter { !it.revoked }

    fun hasCredentials(): Boolean = activeCredentials().isNotEmpty()

    fun putChallenge(record: ChallengeRecord) {
        challenges = challenges + (record.challengeId to record)
    }

    fun getChallenge(challengeId: String): ChallengeRecord? = challenges[challengeId]

    fun consumeChallenge(challengeId: String): ChallengeRecord? {
        val existing = challenges[challengeId] ?: return null
        if (existing.consumed) return null
        val consumed = existing.copy(consumed = true)
        challenges = challenges + (challengeId to consumed)
        return consumed
    }

    fun saveSession(tokens: SessionTokens) {
        prefs.edit()
            .putString(KEY_ACCESS, tokens.accessToken)
            .putString(KEY_REFRESH, tokens.refreshToken)
            .putString(KEY_MSISDN, tokens.msisdn)
            .apply()
    }

    fun loadSession(): SessionTokens? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val msisdn = prefs.getString(KEY_MSISDN, null) ?: return null
        return SessionTokens(access, refresh, msisdn)
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_MSISDN)
            .apply()
    }

    fun clearAll() {
        challenges = emptyMap()
        prefs.edit().clear().apply()
    }

    private fun loadCredentials(): List<StoredCredential> {
        val raw = prefs.getString(KEY_CREDENTIALS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        StoredCredential(
                            credentialId = o.getString("credentialId"),
                            userId = o.getString("userId"),
                            msisdn = o.getString("msisdn"),
                            enrollmentBinding = o.getString("enrollmentBinding"),
                            createdAtEpochMs = o.getLong("createdAtEpochMs"),
                            revoked = o.optBoolean("revoked", false),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeCredentials(list: List<StoredCredential>): String {
        val array = JSONArray()
        list.forEach { c ->
            array.put(
                JSONObject()
                    .put("credentialId", c.credentialId)
                    .put("userId", c.userId)
                    .put("msisdn", c.msisdn)
                    .put("enrollmentBinding", c.enrollmentBinding)
                    .put("createdAtEpochMs", c.createdAtEpochMs)
                    .put("revoked", c.revoked),
            )
        }
        return array.toString()
    }

    companion object {
        private const val KEY_CREDENTIALS = "credentials"
        private const val KEY_ACCESS = "access"
        private const val KEY_REFRESH = "refresh"
        private const val KEY_MSISDN = "msisdn"
    }
}
