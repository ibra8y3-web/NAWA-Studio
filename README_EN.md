<div align="center">
  <img src="docs/assets/nawa-banner.png" alt="NAWA Studio banner" width="100%" />
  <h1>NAWA Studio</h1>
  <p><strong>A local-first Android development workspace with a code editor, an experimental visual designer, and Android source generation.</strong></p>
  <p><a href="README.md">العربية</a> · <a href="docs/NAWASCRIPT_AR.md">NawaScript reference (Arabic)</a> · <a href="docs/ROADMAP_AR.md">Roadmap (Arabic)</a> · <a href="docs/VALIDATION_AR.md">Validation log (Arabic)</a></p>
  <p>
    <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Android" />
    <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
    <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
    <img src="https://img.shields.io/badge/Build-Gradle%208.13-02303A?logo=gradle&logoColor=white" alt="Gradle" />
    <img src="https://img.shields.io/badge/Release-Source%20only-596579" alt="Source only" />
  </p>
</div>

> **Important:** This repository contains source code, not a ready-to-install APK. A GitHub Actions workflow is configured to attempt a Debug APK build and upload it as an artifact, but a successful run must be confirmed in the repository's Actions page. The app has not been verified on a physical Android device from the packaging environment.

## Contents

- [Overview](#overview)
- [Visuals and diagrams](#visuals-and-diagrams)
- [Current capabilities](#current-capabilities)
- [Architecture](#architecture)
- [Requirements and build](#requirements-and-build)
- [Automatic GitHub build](#automatic-github-build)
- [Repository layout](#repository-layout)
- [Known limitations](#known-limitations)

## Overview

**NAWA Studio** is an Android project built with Kotlin and Jetpack Compose. It combines source editing, project/file management, an experimental NawaScript parser and preview, and Android source-project generation for supported language constructs. The goal is a local-first environment that can use GitHub Actions when an APK build is needed.

This is an early-stage project. Having an editor or a source generator does not mean the environment is feature-equivalent to Android Studio. Known limitations are stated below and in the [validation log](docs/VALIDATION_AR.md).

## Visuals and diagrams

### Workspace concept

<img src="docs/assets/workspace-illustration.png" alt="Conceptual illustration of the editor, project explorer, design canvas and inspector" width="100%" />

<sub>This is a purpose-made concept illustration, **not a screenshot of the running app**. It does not imply that every panel shown is implemented.</sub>

### Automated build pipeline

<img src="docs/assets/build-pipeline.png" alt="GitHub Actions build pipeline" width="100%" />

### Architecture map

<img src="docs/assets/architecture-map.png" alt="NAWA Studio architecture map" width="100%" />

## Current capabilities

| Area | Included in source | Limitations |
|---|---|---|
| Code editor | Sora editor, tabs, save behavior, line numbers, TextMate highlighting for multiple languages, project-wide text search | Highlighting is not semantic analysis; no full debugger or language server for every language |
| Project management | File and directory operations, resource import, `.nsqpack` packages, local revision history | Android storage and permission paths still need device-level validation |
| Visual designer | Basic elements, drag movement, `.at(x, y)` positions in source, scrollable preview | Not a complete Android widget designer or responsive constraint-layout editor |
| NawaScript | Parser and preview for a limited subset, simple state types, screens/navigation, events, conditions, arithmetic and bounded loops | Experimental language; see the [language reference](docs/NAWASCRIPT_AR.md) |
| Android generator | Generates Gradle project files and Kotlin/Compose source for supported constructs | Does not compile APKs inside the app and cannot guarantee every generated project will build |
| CI | GitHub Actions workflow for Gradle tests, Debug APK generation, signature verification, and artifact upload | Workflow is configured; a successful run has not yet been independently confirmed |

## Architecture

The architecture map above summarizes the links between the Compose UI, editor, local repository, NawaScript parser, and Android generator. APK generation is configured in the external CI workflow, not inside the app. Editable SVG originals are kept in `docs/assets/`.

## Requirements and build

### Requirements

- JDK 17
- Gradle 8.13
- Android SDK Platform 35
- Android Build Tools 35.0.0

Open the folder containing `settings.gradle.kts` in Android Studio, ensure the SDK packages above are installed, then run from the project root:

```bash
gradle --no-daemon testDebugUnitTest assembleDebug
```

The expected APK path after a successful build is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

> The archive does not include `gradlew` or an APK. You need a suitable Gradle installation or the included GitHub Actions workflow. Compiling isolated Kotlin modules does not prove the full Android application compiles.

## Automatic GitHub build

Workflow: [`.github/workflows/android.yml`](.github/workflows/android.yml).

It runs on pushes to `main` or `master`; it can also be started from **GitHub → Actions → Build NAWA Studio APK → Run workflow**. It checks out the source, installs JDK 17, Gradle 8.13 and Android SDK packages, runs `testDebugUnitTest` and `assembleDebug`, verifies that the APK exists and its signature is valid, and uploads an artifact named `nawa-studio-debug-apk`.

After the workflow finishes green, open that run and download the APK from **Artifacts**. This is a Debug build, not a Release package signed with your own publishing key. A failed run must be diagnosed from its logs; uploading source alone does not mean a successful build has occurred.

## Repository layout

```text
NAWA-Studio/
├── .github/workflows/android.yml   # GitHub Actions APK workflow
├── app/src/main/java/com/nawa/studio/
│   ├── MainActivity.kt             # App UI
│   ├── NawaTextMate.kt             # Syntax-highlighting integration
│   └── core/                       # Language, repository, audit, Android generator
├── app/src/main/assets/textmate/   # Language grammars
├── app/src/test/                   # Unit tests
├── docs/assets/                    # SVG diagrams and illustrations
├── docs/                           # Language docs, roadmap, validation notes
├── scripts/                        # Helper checks
├── RELEASE_STATUS_AR.md            # Release status and validation boundaries
├── SECURITY_AR.md                  # Security guidance
└── README.md                       # Default GitHub project page
```

## Known limitations

- NawaScript is a small experimental language, not a general replacement for Kotlin or Java.
- Preview runs only supported NawaScript constructs; it does not run Kotlin/Java.
- Kotlin/Java editing does not yet provide full semantic analysis, refactoring, or debugging comparable to Android Studio.
- The visual designer only supports basic elements and absolute positions; it is not a full responsive constraint-layout editor.
- The generator prepares source files and cannot guarantee every generated project builds without changes.
- An APK was not built or tested on a physical device in the packaging environment. See the [validation log](docs/VALIDATION_AR.md) and [release status](RELEASE_STATUS_AR.md).

## Contributing and security

See [contribution guidelines](CONTRIBUTING_AR.md) and [security notes](SECURITY_AR.md). Never commit GitHub tokens, API keys, or passwords. Use GitHub Secrets for CI secrets.
