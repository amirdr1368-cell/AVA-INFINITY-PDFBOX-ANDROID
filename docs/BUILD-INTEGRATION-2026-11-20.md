# AVA INFINITY Unified Mobile Build

**Date:** 2026-11-20  
**Target repository:** `amirdr68-commits/AVA-INFINITY-PDFBOX-ANDROID`  
**Integration branch:** `ava/mobile-app-integration`  
**Application source baseline:** `amirdr1368-cell/AVA-INFINITY-ANDROID-V2`, branch `implementation/glass-ui-2.0-approved`  
**PDFBox baseline:** `ava/inline-image-memory-guard`

## Scope

- Bring the approved AVA Android application source, manifest, and unit tests into the PDFBox repository as the `:app` module.
- Keep the PDFBox Android library as the `:library` module and add it as an application dependency.
- Preserve the existing PDFBox `:sample` source directory without including that demonstration app in the unified build.
- Preserve the bounded inline-image parser guard on the integration branch.
- Keep the original `master` branch unchanged.

## Build configuration

- Android Gradle Plugin: 8.7.3
- Kotlin Android plugin: 2.0.21
- Gradle: 8.9
- JDK: 17
- Compile / target SDK: 35
- AVA min SDK: 23
- PDFBox min SDK: 14
- APK variant: debug, installable and debug-signed by the Android Gradle Plugin.

## Build and verification

GitHub Actions workflow: `.github/workflows/android-build.yml`

The workflow runs the AVA and PDFBox debug unit-test tasks, assembles `:app:assembleDebug`, and uploads `AVA-INFINITY-APK` from `app/build/outputs/apk/debug/`.

The previous V2 repository note that APK generation was paused reflected the earlier owner instruction. Amir explicitly authorized unified integration and APK generation on 2026-11-20; that newer instruction supersedes the earlier pause.

## Boundaries and pending verification

- Source transfer and static integration do not prove compilation or runtime correctness.
- The APK is a debug build, not a production release signed with an owner-managed release key.
- The PDFBox sample demonstration app is preserved but is not part of the AVA APK.
- The approved AVA source includes profile-photo selection and the GLASS UI baseline. A separate full-body wardrobe launch-screen requirement must not be described as implemented unless it is added and verified in source.
- A successful Actions run on this exact integration branch and commit is required before claiming that the APK was built.
- Physical-device installation and behavior on the Redmi Note 11 remain separate verification steps.
