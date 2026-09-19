# Android Mario Game

A 2D platform game similar to Mario, built with Kotlin.

## Features
- Move left/right and jump
- Collect coins
- Avoid enemies
- Gravity physics
- Score tracking
- Multiple levels (optional)

## Tech Stack
- Kotlin
- Android (Canvas-based game)
- GitHub Actions (CI/CD for APK builds)

## Build APK
```bash
./gradlew assembleDebug
```

The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

## GitHub Actions
The `.github/workflows/build.yml` file will automatically build the APK on every push.
