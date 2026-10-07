# Local release-candidate validation

Validated on 2026-10-07 at commit
`bb51a73d9c9bd33162ed650ab50932e38b6867bb`.

## Result

The candidate is **not ready for release**. The unsigned release artifacts build,
the unit tests and permission gate pass, and static 16-kilobyte page-alignment
checks pass. The required `lintRelease` gate fails with two errors, however, and
the available emulator images were not stable enough to complete functional or
runtime page-size testing.

Ticket 374 tracks the lint blocker, and ticket 375 tracks the stale Room schema
export path. Both are ordered after this validation. Release follow-up ticket
359 now waits for them.

No version was changed. No tag, push, upload, publication, or real release
credential was used.

## Environment

| Item | Observed value |
| --- | --- |
| Host | Linux 7.0.0-38-generic, amd64 |
| Source commit | `bb51a73d9c9bd33162ed650ab50932e38b6867bb` |
| Java Development Kit (JDK) | OpenJDK 17.0.20.1 |
| Gradle | 8.14.4 |
| Android Gradle Plugin | 8.13.2 |
| `just` | 1.45.0 |
| Android Software Development Kit (SDK) | compile/target 36, minimum 26 |
| Android build tools | 36.0.0; Android Asset Packaging Tool 2.20-13193326; APK signer 0.9 |
| Android emulator | 37.2.12.0, build 16428233 |
| Application identifier | `ca.mattmccormick.phone_sense` |
| Candidate version | name `0.1.0`, code `1` |

Commands using Gradle ran with `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` or
through the `just` JDK 17 selector. Signing variables were removed for unsigned
builds. A project-scoped Gradle cache was used; candidate artifacts were built
from source in the ticket worktree and a separate clean clone.

## Release gates

| Gate | Result | Evidence |
| --- | --- | --- |
| `just test` | Pass | Permission-checker tests passed; debug and release unit-test tasks passed. |
| `just build` | Pass | Produced `app-release-unsigned.apk` and logged that release signing was not configured. |
| Release permission check | Pass | The exact unsigned release Android Package (APK) contained only allowlisted permissions. |
| `lintRelease` | **Fail** | Two `NewApi` errors; 23 warnings. |
| `bundleRelease` | Pass | Produced `app-release.aab` even though the combined Gradle invocation ultimately failed on lint. It also passes when run alone. |

The lint errors are:

- `app/src/main/res/values/themes.xml:7` uses
  `android:windowLightNavigationBar`, which requires Application Programming
  Interface (API) level 27, in an unqualified resource while the minimum is 26.
- `app/src/main/res/values-night/themes.xml:5` has the same problem.

The remaining lint output consists of warnings. The build also reports that it
cannot strip `libandroidx.graphics.path.so` and
`libdatastore_shared_counter.so`, so it packages those libraries unchanged.

The build generated an untracked Room schema at
`app/schemas/ca.mattmccormick.phone_sense.data.UsageDatabase/4.json`. The
repository currently tracks versions 1 through 4 under the former
`ca.mattmccormick.screenbudget` path. Generated schema output was removed from
this validation commit. Ticket 375 tracks reconciliation before relying on
committed schemas for migration review.

## Package inspection

`aapt2 dump badging` on the unsigned release APK reports:

- package `ca.mattmccormick.phone_sense`;
- version name `0.1.0` and version code `1`;
- minimum API level 26 and target API level 36;
- display name `Phone Sense`;
- native code for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.

The merged package permissions are:

- `android.permission.PACKAGE_USAGE_STATS`;
- `android.permission.POST_NOTIFICATIONS`;
- `android.permission.WAKE_LOCK`;
- `android.permission.ACCESS_NETWORK_STATE`;
- `android.permission.RECEIVE_BOOT_COMPLETED`;
- `android.permission.FOREGROUND_SERVICE`;
- `ca.mattmccormick.phone_sense.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.

`android.permission.INTERNET` is absent. `apksigner verify` rejects the unsigned
APK with `DOES NOT VERIFY`, as expected. `jarsigner -verify` likewise reports
that the clean-clone Android App Bundle (AAB) is unsigned.

## Clean unsigned build

A new local clone was created from the source worktree at the commit above. It
started with no changes, no `keystore.properties`, no Java KeyStore files, and
no `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, or `KEY_PASSWORD`
variables. `just build` completed and produced
`app-release-unsigned.apk` without changing or inventing a private credential.
`bundleRelease` also completed in that clone.

The clean build generated the same untracked Room schema path noted above. The
unsigned APK checksum differs from the worktree artifact because the Android
Gradle Plugin wrote valid Git revision metadata in the clean clone but wrote
`NO_VALID_GIT_FOUND` for the linked worktree. No reproducibility claim is made.

## Native-library page alignment

The unsigned APK contains two native libraries for each of its four application
binary interfaces, for eight files total. All eight are stored uncompressed.

`zipalign -c -P 16 -v 4` reports `Verification successful`; each native-library
entry begins on a 16-kilobyte boundary. `readelf -lW` reports `0x4000` alignment
for every loadable segment in all eight libraries.

Static inspection supports 16-kilobyte compatibility but does not prove runtime
compatibility.

## Emulator validation

A disposable PKCS #12 test identity was created in `/tmp`, valid for two days,
with certificate subject `CN=Delegator 356 Disposable Test`. It is unrelated to
the Google Play upload key or app-signing key and must never be distributed.
The test-signed release APK verified with APK Signature Scheme v2 and installed
once on the 16-kilobyte emulator before that emulator became unstable. The
temporary private key was deleted after the test.

The dedicated Android 17 16-kilobyte image reported a runtime page size of
16,384 bytes. The platform then repeatedly lost its package and activity
services. Its crash buffer showed repeated platform ultra-wideband service
failures and `surfaceflinger` aborts in `RegionSampling` with:

`Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma`

The same `surfaceflinger` failure occurred with a separate standard 4-kilobyte
image. Attempts used headless software rendering with Vulkan disabled, headless
host rendering, and windowed host rendering. Package installation failed with a
broken pipe or the package/activity services disappeared. This is an emulator
platform/renderer failure before app execution, rather than an observed app
crash.

Consequently, none of these functional cases was completed:

- fresh setup and usage-access onboarding;
- notification permission denial and skip behavior;
- goal creation;
- app exclusions;
- usage collection;
- daily and weekly reminders;
- Today and chart widgets;
- export and import;
- light and dark mode;
- retained data after reinstall-with-update.

There is no prior published release proven in this repository. No production
upgrade test was performed or claimed. The reinstall-with-update case would
have used the disposable local identity only.

Runtime behavior on a 16-kilobyte-page device remains unproven despite the
passing static checks. Repeat the functional matrix on a stable dedicated
emulator or device after ticket 374 passes the release gates.

## Artifact checksums

These SHA-256 checksums identify the safe, unsigned artifacts from the clean
clone. They are local validation outputs and were not committed or distributed.

| Artifact | SHA-256 |
| --- | --- |
| `app-release-unsigned.apk` | `9ec6bbade8636be7f31f939c341afe81bef281f209a7f0a67a488a980e07a891` |
| `app-release.aab` | `f5441f4e247b2a4ea9954f1de74d1ca4962388bfac9f1c6178c0f74aa7f85902` |

For comparison, the worktree unsigned APK was
`39bef68f1227625454b03977bfca20a300b13efb6c3e109568a84a13408363d2`;
its differing embedded version-control metadata explains why it does not match
the clean-clone APK.
