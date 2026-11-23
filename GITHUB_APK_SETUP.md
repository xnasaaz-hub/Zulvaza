# GitHub APK Build Setup — ZULVAZA Seller Center

This project is pre-configured with a GitHub Actions workflow at `.github/workflows/build-apk.yml` that builds your Android APK automatically in the cloud—no Android Studio installation required.

## 1. Push Project to GitHub
Run these commands inside the project folder (or use AI Studio's **Push to GitHub** button):

```bash
git init
git add .
git commit -m "ZULVAZA Seller Center Android app with GitHub APK workflow"
git branch -M main
git remote add origin https://github.com/<YOUR_USERNAME>/<YOUR_REPO>.git
git push -u origin main
```

## 2. Download Your Debug APK
1. Open your repository on GitHub and click the **Actions** tab.
2. Open the **Build ZULVAZA Seller APK** workflow run (you can also trigger it manually anytime via **Run workflow**).
3. Scroll down to **Artifacts** and download **`zulvaza-seller-debug-apk`** to get `app-debug.apk`.

## 3. Recommended: Signed Release APK & AAB (Avoids Play Protect Debug-Key Warnings)
Sideloading an `app-debug.apk` signed with a generic debug key often triggers a Google Play Protect "unrecognized developer / harmful app" warning on Android devices. To generate a properly signed `app-release.apk` and `app-release.aab` in GitHub Actions, add these 3 repository secrets under **Settings → Secrets and variables → Actions**:
- `RELEASE_KEYSTORE_BASE64`: output of `base64 -w 0 my-upload-key.jks`
- `STORE_PASSWORD`: your keystore password
- `KEY_PASSWORD`: your `upload` key alias password
