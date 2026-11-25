# GitHub APK Build & Signing Setup — ZULVAZA Seller Center

This project includes a GitHub Actions workflow at `.github/workflows/build-apk.yml` that builds, signs, and publishes a **GitHub Release** automatically on every push to `main` (so in-app **Check for Updates** immediately detects new versions).

## 1. Current Signing Status
- **Debug APK (`debug.keystore`)**: Automatically signed during every build using the repository's `debug.keystore.base64`. Good for testing and sideloaded debug updates.
- **Release APK / AAB (`my-upload-key.jks`)**: **Not signed until you add your 3 signing secrets to GitHub.** `app/build.gradle.kts` expects key alias `upload`.

## 2. Create Your Release Signing Key (One-Time)
Run this on your computer (with Java JDK installed):

```bash
keytool -genkeypair -v -keystore my-upload-key.jks \
  -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

Then convert the `.jks` file to a single-line Base64 string:
- **Linux / macOS**: `base64 -w 0 my-upload-key.jks` (macOS: `base64 -i my-upload-key.jks | tr -d '\n'`)
- **Windows PowerShell**: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("my-upload-key.jks"))`

> Keep `my-upload-key.jks` and your passwords backed up safely outside the repo. Never commit `.jks` files to GitHub.

## 3. Add 3 Secrets in GitHub
Open your GitHub repository → **Settings → Secrets and variables → Actions → New repository secret**, and add:
1. `RELEASE_KEYSTORE_BASE64` — the Base64 string from step 2
2. `STORE_PASSWORD` — your keystore password
3. `KEY_PASSWORD` — your `upload` key password

## 4. Publishing an In-App Update
1. Increment `versionCode` and `versionName` in `app/build.gradle.kts` (for example `versionCode = 4`, `versionName = "1.3.0"`).
2. Commit and push to `main` (or trigger **Run workflow** manually in **Actions**).
3. GitHub Actions creates/updates the release tag (`v1.3.0`), attaches the signed APK(s), and marks it as **Latest**.
4. Inside ZULVAZA Seller Center → **Settings → App Updates → Check for Updates**, sellers will see the update and can download & install it directly.

> **Important Android Rule**: A Debug-signed app can only update in-place to a newer Debug APK, and a Release-signed app can only update in-place to a newer Release APK signed with the same keystore.
