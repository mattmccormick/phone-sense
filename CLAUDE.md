# Screen Budget

Read the plan first: `/home/matt/notes/20-29 Projects/Screen Budget/Plan.md`.
It holds the permissions, the version support matrix, the data model, the
aggregation rules, and the screens.

## Rules

- Kotlin, Jetpack Compose with Material 3, Room, DataStore, WorkManager, Vico.
  All dependencies must be FOSS (F-Droid rule). No Google Play Services, no
  Firebase, no analytics.
- `minSdk 26`, `targetSdk 36`, `compileSdk 36`. Guard event constants that
  are newer than API 26 by numeric value.
- No `INTERNET` permission. No `QUERY_ALL_PACKAGES`. Nothing leaves the device.
- Test first. Write a failing test, then the code that makes it pass. Core
  logic (aggregation, budget, codec) is pure Kotlin with no Android imports and
  is tested with plain JUnit. Room DAOs and workers use Robolectric.
- Keep a refactor and a behaviour change in separate commits.
- Public copy (store listing, README) uses no em dashes.

## Commands

- `./gradlew test` runs the unit tests.
- `./gradlew assembleRelease` builds the APK.
- JDK 17 is at `/usr/lib/jvm/java-17-openjdk-amd64`; the SDK is at
  `~/Android/Sdk` (platform 36, build-tools 36.0.0 installed).
