# AVA INFINITY Mobile + PDFBox Android

This repository contains the unified AVA INFINITY Android application and the PDFBox-Android library used by the app.

## Modules

- `:app` — AVA INFINITY Android application (Kotlin).
- `:library` — PDFBox-Android library, including the AVA inline-image memory guard.
- `:sample` — original PDFBox demonstration app source, preserved but not included in the unified build.

## Build

Requirements: JDK 17, Android SDK 35, Gradle 8.9.

```bash
./gradlew :app:testDebugUnitTest :library:testDebugUnitTest
./gradlew :app:assembleDebug
```

Installable debug APK:

`app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions runs the same tests and APK assembly on the `ava/**` integration branches and uploads the artifact named `AVA-INFINITY-APK`.

## Project records

- [Unified mobile build and integration record](docs/BUILD-INTEGRATION-2026-11-20.md)
- [AVA mobile implementation status](docs/AVA-MOBILE-IMPLEMENTATION-STATUS.md)
- [GLASS UI 2.0 implementation status](docs/AVA-MOBILE-GLASS-UI-2.0-IMPLEMENTATION-STATUS.md)
- [Approved UI 2.0 design](docs/AVA-MOBILE-UI-2.0-APPROVED.md)
- [Project master change register](docs/PROJECT-MASTER-CHANGE-REGISTER.md)
- [AVA wardrobe register and implementation status](docs/AVA-WARDROBE-REGISTER-2026-11-20.md)
- [PDFBox upstream project](https://github.com/TomRoush/PdfBox-Android)

## Licensing

The PDFBox library's existing Apache 2.0 license and NOTICE files are retained. AVA application source and project records are maintained separately within this repository.
