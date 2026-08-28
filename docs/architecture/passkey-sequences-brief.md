# Passkey sequences — implementation brief

**App:** NChatPasskey (`com.gochat.passkey`)  
**SDD:** `/Volumes/External/Companies/e&/Passkey Authentication/GoChat-Passkey-Authentication-SDD.md` (SDD-AUTH-002)  
**UX ref:** [Android passkeys patterns — Unified sign-in](https://developer.android.com/design/ui/mobile/guides/patterns/passkeys#unified_sign-in)

## Verdict

Single-module Clean Architecture demo: Compose UI → `AuthViewModel` → `AuthInteractor` → `FakeRelyingParty` + `CredentialManagerPasskeyGateway`.

## Module layout

| Package | Role |
|---------|------|
| `domain/` | Models, `RelyingParty`, `PasskeyCeremonyGateway`, `AuthInteractor` |
| `data/` | `FakeRelyingParty`, `CredentialStore`, `CredentialManagerPasskeyGateway`, encoding helpers |
| `ui/auth/` | Screens + `AuthViewModel` |
| `NChatPasskeyApp` | Wires store + Fake RP |
| `MainActivity` | Creates CM gateway with Activity context |

## Sequence mapping

### Flow A — first login (OTP → register)

1. `requestOtp` / `verifyOtp` → enroll session  
2. `startPasskeyRegister` → WebAuthn create options JSON + challenge  
3. `CredentialManager.createCredential` → GPM UV + keypair  
4. `finishPasskeyRegister` → store credential ID + enrollment binding → issue tokens  

### Flow B — later login (no OTP)

1. `startPasskeyLogin` → challenge + get options  
2. `CredentialManager.getCredential` → signed assertion  
3. `finishPasskeyLogin` verification gate:  
   lookup → challenge consume → RP/origin → UP/UV flags → non-empty signature  
4. Issue tokens  

**Trust rule:** login never accepts a FE-supplied public key; only the stored enrollment record is used.

## Crypto note (intentional demo limit)

Full ECDSA/COSE assertion verify against a stored public key is **production backend** work (mature WebAuthn server library). This spike implements the SDD **structural gate** and server-side enrollment binding. Do not ship this Fake RP as production auth.

## Persistence

`CredentialStore` keeps credentials (+ last session) in SharedPreferences so Flow B works after process death. Challenges are in-memory with ~60s TTL.

## Asset Links checklist

- [ ] Own a domain for `BuildConfig.RP_ID`
- [ ] Publish `/.well-known/assetlinks.json` with package `com.gochat.passkey` + cert SHA-256
- [ ] Relation includes `delegate_permission/common.get_login_creds`
- [ ] Rebuild after RP ID change
- [ ] Test on a real device with a passkey provider enabled

## Manual verify

1. First login with OTP `123456` → Create a passkey → success  
2. Kill app → Later login → no OTP  
3. Cancel UV → no session  
4. Broken Asset Links → CM failure message (documented, expected)  
5. Failed later login → OTP recovery path  

## Out of scope

Integrity, Captcha, DTT Silent Auth, iOS, HMS, real GoChat API.
