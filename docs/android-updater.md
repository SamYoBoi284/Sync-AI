# Sync AI Android updater setup

## What the updater does

- Checks the latest stable GitHub Release when Sync AI is opened, at most once every 12 hours, if **Auto Check for Updates** is enabled.
- **Auto Download Updates** controls whether a discovered update starts downloading automatically.
- **Download on** enforces Wi-Fi, Mobile Data, or Both for update downloads. The default is Wi-Fi.
- Downloads the release APK, verifies its SHA-256 checksum, and then opens Android's package installer. Android still requires the user to confirm installation; this app does not silently install updates.
- Manual **Check Now** remains available when automatic checks are disabled.

## Required one-time release setup

Android only permits an in-place app update when the new APK uses the same application ID and signing certificate as the installed APK. The repository's current debug workflow produces debug-signed APKs; those are not a suitable stable signing identity for releases.

1. Generate a persistent release keystore and keep a secure backup. Never commit the keystore or its passwords.
2. Add these four repository **Actions secrets**:
   - `ANDROID_KEYSTORE_BASE64`: base64-encoded bytes of the keystore file.
   - `ANDROID_KEYSTORE_PASSWORD`: keystore password.
   - `ANDROID_KEY_ALIAS`: key alias.
   - `ANDROID_KEY_PASSWORD`: private-key password.
3. Publish a version tag in `vMAJOR.MINOR.PATCH` format, such as `v0.3.0`. The release workflow builds and signs `sync-ai-release.apk`, generates `sync-ai-release.apk.sha256`, and attaches both assets to the GitHub Release.
4. Do not lose the keystore. A different signing certificate cannot update installations signed with the old certificate.

Example keystore generation command (run locally in a private directory):

```sh
keytool -genkeypair -v -keystore sync-ai-release.jks -alias sync-ai -keyalg RSA -keysize 2048 -validity 10000
```

On Linux/macOS, create the base64 secret value with:

```sh
base64 < sync-ai-release.jks | tr -d '\n'
```

On Windows PowerShell, use:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("sync-ai-release.jks"))
```

## First install / migration warning

If the APK currently installed on a phone was signed with a temporary GitHub Actions debug key, Android will reject an update signed with the new persistent release key. That is a signing-identity limitation, not an updater bug. Before switching signing identities, export important chats and keep copies of any imported GGUF/Vosk model files. A one-time uninstall/reinstall may be required and uninstalling removes app-private data. After installing the first APK signed with the persistent release key, future signed releases can update in place and preserve app data.

## Verification notes

The updater expects the two exact release assets named above. The checksum verifies transfer integrity before the package installer is opened; Android also verifies the APK's signing certificate during installation. No release can be published until the four signing secrets are configured.
