# Secretaria

Secretaria is a notes management app built with Kotlin Multiplatform and Compose Multiplatform.

The project started as an Android app and is being migrated step by step into a shared codebase. The goal is to keep the experience simple: sign in, create lists, share them with friends, add notes, and manage everything from a clean cross-platform app.


## What the app does

- Create and manage personal lists
- Share lists with friends
- Add, edit, and delete notes
- Sort lists by name or date
- Manage friendships and shared content


## Platforms

- Android: available now
[Download Secretaria on Google Play](https://play.google.com/store/apps/details?id=com.chemecador.secretaria)

- Desktop (JVM): available from this repository

- iOS: coming soon


## Build for JVM/Desktop


If you want a desktop app package, run:

```bash
./gradlew :composeApp:createDistributable
```

The generated desktop files are written to:

`composeApp/build/compose/binaries/main/app`