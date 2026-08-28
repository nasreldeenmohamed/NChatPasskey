# NChat Passkey

Android concept demo for **GoChat SDD-AUTH-002**: OTP once, then passkey login via Credential Manager + an in-app Fake Relying Party.

## What it demonstrates

| Flow | Steps |
|------|--------|
| **First login** | Phone → mock OTP (`123456`) → Create a passkey → `createCredential` → register/finish → session |
| **Later login** | Sign in with passkey → `getCredential` → authenticate/finish → session (no OTP) |

Private keys stay in the credential provider (typically Google Password Manager). The Fake RP stores credential ID + enrollment binding and runs the SDD verification gate on login.

## Run

1. Open in Android Studio / run `./gradlew :app:assembleDebug`
2. Use a **physical device** with Google Password Manager (or another passkey provider) for the real ceremony
3. Demo OTP is always `123456` (see `BuildConfig.OTP_CODE`)

## Asset Links (required for real GPM)

Without Digital Asset Links, `createCredential` / `getCredential` usually fails.

1. Set `RP_ID` in [`app/build.gradle.kts`](app/build.gradle.kts) to a domain you control (default placeholder: `gochatapp.net`)
2. Host `https://<RP_ID>/.well-known/assetlinks.json`:

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls", "delegate_permission/common.get_login_creds"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.gochat.passkey",
    "sha256_cert_fingerprints": ["<DEBUG_OR_RELEASE_SHA256>"]
  }
}]
```

3. Get the debug SHA-256:

```bash
keytool -list -v -alias androiddebugkey \
  -keystore ~/.android/debug.keystore -storepass android
```

4. Rebuild the app after changing `RP_ID`

## Architecture brief

See [`docs/architecture/passkey-sequences-brief.md`](docs/architecture/passkey-sequences-brief.md).

## Manual test checklist

- [ ] Flow A: OTP `123456` → create passkey → success snackbar
- [ ] Kill app → Flow B → login without OTP
- [ ] Cancel biometric/PIN → no session
- [ ] Later login failure shows **Use OTP recovery**
- [ ] Without Asset Links → CM error message (expected)

## Non-goals

Production GoChat backend, Play Integrity, Captcha, Silent Auth DTT, iOS, HMS.
