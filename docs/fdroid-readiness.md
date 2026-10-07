# F-Droid readiness audit

Audited 2026-10-07 against the current [F-Droid inclusion policy], [submission
guide], and [build metadata reference]. This is preparation evidence, not an
F-Droid approval or a successful `fdroid build`.

## Result

Phone Sense appears suitable for the main F-Droid repository after the release
source becomes buildable and is published at an immutable revision. No
proprietary library, advertising, analytics, tracking software development kit,
remote service dependency, or non-free build tool was found. That conclusion is
based on the resolved release graph and source review, not on the absence of the
`INTERNET` permission.

Current blockers:

1. **The public repository has no release tag or buildable release commit.**
   `https://github.com/mattmccormick/phone-sense.git` was publicly readable and
   its issue tracker was enabled. On 2026-10-07 it advertised only `main` at
   `aa5d1b3f0b9592629b38a8f9364ebe952b47afd9`; `git ls-remote --tags` returned
   no tags. That public revision contains the unescaped apostrophe described
   below. Publish a tested commit and immutable version tag before replacing the
   recipe placeholder.
2. **A clean release at this ticket's source revision fails.** With all signing
   variables unset, `./gradlew clean assembleRelease --offline` fails in
   `mergeReleaseResources` because `usage_access_explanation` contains an
   unescaped apostrophe. Pending ticket 351 already contains the one-character,
   signed-off fix in `a8bbcabe6c06108e85b81cc9acc52778738c9966`, so no
   duplicate ticket was opened. With exactly that fix applied temporarily, the
   same command produced `app-release-unsigned.apk`; `apksigner verify` reported
   `DOES NOT VERIFY`, as expected. Re-run from the eventual public release tag.
3. **The proposed metadata has not passed F-Droid tooling.** `fdroidserver` was
   unavailable here, so no `fdroid scanner`, `fdroid lint`, or isolated
   `fdroid build` was claimed. Build-server availability of the selected Gradle,
   Android, and Java versions also remains to be proven.
4. **Signing ownership remains an owner choice.** The proposal below assumes
   ordinary F-Droid signing. It needs no upstream private key, but packages from
   another signing channel normally cannot update an F-Droid installation.

## Source, license, and application behavior

- Canonical source and issues: [GitHub repository] and [issue tracker]. The live
  repository was public, unarchived, and licensed; the checkout's `README.md`
  declares `GPL-3.0-or-later` and `LICENSE` contains GNU General Public License
  version 3 terms. Root licensing covers the Kotlin, Extensible Markup Language
  (XML), icon source, and store assets; no separately restricted asset was found.
- Application identifier `ca.mattmccormick.phone_sense`, version `0.1.0` (code
  1), minimum Android 8.0 / Android Application Programming Interface (API) 26,
  and target/compile API 36 are static in `app/build.gradle.kts`.
- The release manifest requests `PACKAGE_USAGE_STATS` and `POST_NOTIFICATIONS`.
  It does not request `INTERNET`. Source and build-script searches found no
  Firebase, Google Play services, Crashlytics, ad, analytics, or dynamic code
  download integration. Android backup remains enabled and is disclosed; it is
  operating-system behavior rather than an app network integration.
- The repository contains no checked-in application archive, Android archive,
  Java archive other than the Gradle wrapper, or native library. The committed
  wrapper Secure Hash Algorithm 256 (SHA-256) value is
  `7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172`,
  matching Gradle's [published 8.14.4 wrapper checksum].

## Resolved dependencies

Gradle resolved `releaseRuntimeClasspath` offline from only `google()` and
`mavenCentral()`. Plugin resolution also declares the Gradle Plugin Portal;
these three repositories are on F-Droid's [scanner allowlist]. Direct release
libraries are Core, Lifecycle, Activity, Compose, Material 3, Room, WorkManager,
DataStore, and Kotlin serialization. The relevant resolved families were:

| Family | Resolved versions and evidence |
| --- | --- |
| AndroidX | Activity 1.11.0, Compose 1.10.6, Material 3 1.4.0, Core 1.17.0, Lifecycle 2.9.4, Room 2.8.5 / SQLite 2.6.2, WorkManager 2.12.0, DataStore 1.2.1, Graphics Path 1.1.0, plus AndroidX support transitives. Their published metadata names Apache-2.0 and canonical [AndroidX source]. |
| Kotlin | Standard library and compiler plugins 2.4.20, coroutines 1.9.0, and serialization 1.8.1. Canonical [Kotlin], [coroutines], and [serialization] sources are Apache-2.0. |
| Other transitives | [Okio] 3.9.1, [JetBrains annotations] 23.0.0, [JSpecify] 1.0.0, and Guava [ListenableFuture] 1.0 have public source and Apache-2.0 metadata. |
| Repackaged Protocol Buffers | `datastore-preferences-external-protobuf` 1.2.1 repackages `protobuf-lite`; its [AndroidX build definition] identifies the relocation and Berkeley Software Distribution (BSD) 3-Clause license, and Protocol Buffers has [public source]. This is an audited exception to the general AndroidX Apache-2.0 statement. |

The merged release contains two native library names for Arm 32/64-bit and x86
32/64-bit: `libandroidx.graphics.path.so` and
`libdatastore_shared_counter.so`. They arrive inside trusted Google Maven
Android archives rather than this repository. Canonical source includes the
[Graphics Path C++ tree] and [DataStore C++ tree]; inspected source headers and
published archive notices are Apache-2.0. F-Droid policy permits prebuilt free
software from trusted Maven repositories, subject to scanner and reviewer
confirmation.

Build plugins and tools are Android Gradle Plugin 8.13.2, Kotlin/Compose and
serialization plugins 2.4.20, Kotlin Symbol Processing 2.3.12, Room plugin
2.8.5, and Gradle 8.14.4. Their sources are respectively [Android build tools],
[Kotlin], [KSP], [AndroidX source], and [Gradle], under free software licenses.
The build requires OpenJDK (the project targets Java 17), Android Software
Development Kit platform 36, and build-tools 36.0.0. F-Droid explicitly permits
official Android Software Development Kit prebuilts; use OpenJDK, not Oracle's
proprietary distribution. No Android Native Development Kit build runs in this
project: the two native libraries are Maven dependency payloads.

## Minimal proposed fdroiddata recipe

**UNVERIFIED — DO NOT SUBMIT AS WRITTEN.** Replace the placeholder only with the
full hash or immutable tag of the tested, publicly accessible release commit.
Confirm OpenJDK 17 selection and Android API/build-tools availability in the
current F-Droid build image; add setup commands only if that test requires them.

```yaml
Categories:
  - Time
License: GPL-3.0-or-later
AuthorName: Matt McCormick
SourceCode: https://github.com/mattmccormick/phone-sense
IssueTracker: https://github.com/mattmccormick/phone-sense/issues
RepoType: git
Repo: https://github.com/mattmccormick/phone-sense.git

Builds:
  - versionName: 0.1.0
    versionCode: 1
    commit: <FULL_RELEASE_COMMIT_OR_TAG>
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 0.1.0
CurrentVersionCode: 1
```

After the tag exists, validate in an `fdroiddata` checkout with `fdroid readmeta`,
`fdroid rewritemeta`, `fdroid lint`, and `fdroid build`. Inspect both source and
produced-package scanner results. Do not add `scanignore` for the wrapper or
native libraries unless review establishes a narrow, documented need.

[F-Droid inclusion policy]: https://f-droid.org/docs/Inclusion_Policy/
[submission guide]: https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
[build metadata reference]: https://f-droid.org/docs/Build_Metadata_Reference/
[scanner allowlist]: https://gitlab.com/fdroid/fdroidserver/-/blob/master/fdroidserver/scanner.py
[GitHub repository]: https://github.com/mattmccormick/phone-sense
[issue tracker]: https://github.com/mattmccormick/phone-sense/issues
[published 8.14.4 wrapper checksum]: https://services.gradle.org/distributions/gradle-8.14.4-wrapper.jar.sha256
[AndroidX source]: https://android.googlesource.com/platform/frameworks/support/
[Kotlin]: https://github.com/JetBrains/kotlin
[coroutines]: https://github.com/Kotlin/kotlinx.coroutines
[serialization]: https://github.com/Kotlin/kotlinx.serialization
[Okio]: https://github.com/square/okio
[JetBrains annotations]: https://github.com/JetBrains/java-annotations
[JSpecify]: https://github.com/jspecify/jspecify
[ListenableFuture]: https://github.com/google/guava
[AndroidX build definition]: https://android.googlesource.com/platform/frameworks/support/+/androidx-main/datastore/datastore-preferences-external-protobuf/build.gradle
[public source]: https://github.com/protocolbuffers/protobuf
[Graphics Path C++ tree]: https://android.googlesource.com/platform/frameworks/support/+/androidx-main/graphics/graphics-path/src/main/cpp/
[DataStore C++ tree]: https://android.googlesource.com/platform/frameworks/support/+/androidx-main/datastore/datastore-core/src/androidMain/cpp/
[Android build tools]: https://android.googlesource.com/platform/tools/base/
[KSP]: https://github.com/google/ksp
[Gradle]: https://github.com/gradle/gradle
