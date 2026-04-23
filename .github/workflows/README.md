# GitHub Actions Workflows

This directory contains automated build and test workflows for the Banking Statement app.

## Available Workflows

### 1. CI (`ci.yml`)
**Triggers:** Every push and pull request to main/develop branches

**What it does:**
- ✅ Checks code formatting (ktlint)
- ✅ Compiles Android and iOS code
- ✅ Runs fast validation checks

**Purpose:** Quick feedback on code quality

---

### 2. Android Build (`android-build.yml`)
**Triggers:** Push to main/develop/claude branches, PRs to main/develop

**What it does:**
- 🤖 Builds Debug APK for testing
- 🧪 Runs unit tests
- 📦 Uploads APK as artifact (available for 7 days)
- 🚀 Builds Release APK on main branch (available for 30 days)

**How to get the APK:**
1. Go to Actions tab in GitHub
2. Click on the workflow run
3. Scroll to "Artifacts" section
4. Download `android-debug-apk` or `android-release-apk`

---

### 3. iOS Build (`ios-build.yml`)
**Triggers:** Push and PRs to main/develop

**What it does:**
- 🍎 Builds iOS framework for simulator
- 📱 Compiles iOS app for simulator
- 📋 Uploads build logs

**Note:** iOS builds use macOS runners (expensive on GitHub Actions)

---

## Build Status

You can add build badges to your README:

```markdown
![CI](https://github.com/andybuki/BankingStatement/workflows/CI/badge.svg)
![Android Build](https://github.com/andybuki/BankingStatement/workflows/Android%20Build/badge.svg)
```

## Downloading Built Apps

After each successful build:

1. Navigate to **Actions** tab in GitHub
2. Select the workflow run (e.g., "Android Build #42")
3. Scroll to **Artifacts** section at the bottom
4. Click to download the APK

## Local Testing

To test locally before pushing:

```bash
# Android
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:testDebugUnitTest

# iOS (requires macOS)
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
cd iosApp && xcodebuild -scheme iosApp build
```

## Troubleshooting

**Build failing?**
- Check the workflow logs in Actions tab
- Ensure `gradlew` has execute permissions: `chmod +x gradlew`
- Verify JDK 17 is being used

**iOS build issues?**
- Ensure Xcode version matches workflow (15.2)
- Check iOS simulator availability
- macOS runners may have different Xcode versions installed

## Cost Optimization

GitHub Actions minutes:
- **Linux runners:** ✅ Fast and free (2,000 min/month on free plan)
- **macOS runners:** ⚠️ 10x more expensive (counts as 10 minutes)

**Tips:**
- Use `continue-on-error: true` for optional steps
- Only run iOS builds on main/develop branches
- Cache Gradle dependencies (already configured)
