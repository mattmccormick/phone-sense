# Releasing Phone Sense

Release checklist for Google Play and the official F-Droid repository. Checked
against the linked official guidance on 2026-10-05; recheck store requirements
before each submission. This guide does not mean the app has been published.

## Current project state

| Item | Value or location |
| --- | --- |
| Display name | Phone Sense |
| Application identifier | `ca.mattmccormick.screenbudget` — retain this for updates |
| Version | `versionName = "0.1.0"`, `versionCode = 1` in `app/build.gradle.kts` |
| Android support | Minimum 26, target and compile 36 |
| Build environment | Java Development Kit (JDK) 17; Android Software Development Kit (SDK) platform 36 and build-tools 36.0.0 |
| License | GNU General Public License (GPL), version 3 or later; see `LICENSE` |
| Store text and artwork | `fastlane/metadata/android/en-US/` |
| Signing configuration | `keystore.properties` or environment variables, consumed by `app/build.gradle.kts` |

An Android Package (APK) installs on a device. An Android App Bundle (AAB) is
uploaded to Google Play, which generates installable packages. F-Droid builds
packages from public source using its own build recipe.

## Before the first release

- [ ] Publish the complete source and license to a public Git repository. No Git
  remote was configured in this checkout when this guide was written. Choose a
  canonical repository address and an issue tracker.
- [ ] Decide signing ownership before distributing any release; see below.
- [ ] Create developer accounts and complete the identity/contact verification
  requested by Google Play Console. Check application registration under
  [Android developer verification](https://developer.android.com/developer-verification)
  for the distribution channels and countries you intend to support.
- [ ] Publish a stable, public privacy-policy page based on `PRIVACY.md`.
  Add a privacy contact and make the policy available inside the app. The current
  About screen contains license information but no privacy policy.
- [ ] Reconcile privacy statements with actual backup behavior. The manifest
  enables Android backup, and `backup_rules.xml` and `data_extraction_rules.xml`
  include the usage database and preferences for cloud backup and device transfer.
  The present “everything stays on your device” wording needs qualification, or
  backup behavior needs changing, before release. Check onboarding and store text
  as well as the policy. Review the
  [Google Play privacy requirements](https://support.google.com/googleplay/android-developer/answer/18258653).
- [ ] Prepare the missing Play feature graphic, refresh screenshots after the
  theme change, and review the existing descriptions and `changelogs/1.txt`.
- [ ] Fix the `just permissions` allowlist before using it as a release gate:
  it currently rejects the app's intentional `POST_NOTIFICATIONS` permission.
  Continue rejecting unexpected permissions, especially `INTERNET`.

## Choose signing ownership

Android updates require a compatible signing identity as well as the same
application identifier. Keep a secure backup of private keys and passwords.
Never commit or send them to a store reviewer.

| Distribution | Signing arrangement |
| --- | --- |
| Google Play | Sign the uploaded bundle with an upload key; Play App Signing signs the packages delivered to users. |
| Standard F-Droid | F-Droid builds from source and signs with its own app key. No developer private key is provided. |
| F-Droid reproducible build | F-Droid verifies its build against a published developer-signed package and can distribute that signing identity. |

The simplest operational choice is Play App Signing plus standard F-Droid
signing. Those installs normally cannot update each other. Explain to users that
switching channels requires exporting data, uninstalling, reinstalling, and
importing the backup. The same issue applies when moving from the current debug
installation to a release signed by a different key.

If seamless channel switching is a requirement, design and test it before launch.
Use a developer-controlled app-signing key enrolled in Play and a compatible
F-Droid reproducible-build workflow. A Play **upload key** alone is not the
identity used for installed Play packages; matching it does not solve this.
Matching certificates also does not prove reproducibility or validate split
package compatibility. Verify the actual delivered artifacts.
See [Android app signing](https://developer.android.com/studio/publish/app-signing)
and [F-Droid reproducible builds](https://f-droid.org/docs/Reproducible_Builds/).

## Configure local release signing

If a release key already exists, use it rather than creating a replacement.
Otherwise, from the project root, create a keystore interactively:

```sh
keytool -genkeypair -v -keystore release.jks -storetype PKCS12 \
  -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Create `keystore.properties` in the project root:

```properties
storeFile=release.jks
storePassword=YOUR_KEYSTORE_PASSWORD
keyAlias=upload
keyPassword=YOUR_KEYSTORE_PASSWORD
```

Use the same password for both properties with the keystore format above. Protect
both files; `.gitignore` already excludes `*.jks`, `*.keystore`, and
`keystore.properties`. A file under `.gitignore` still needs a secure backup.
The alias `upload` is only a label; the key's role depends on the signing plan.

Automated builds can instead supply `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, and `KEY_PASSWORD` through a secret store. Properties take precedence
over environment variables. Partial signing configuration fails the build;
with no configuration, release packages are unsigned.

## Prepare each release

1. Update `versionName` and increase `versionCode` in `app/build.gradle.kts`.
   Every newly uploaded Play build needs an unused, higher code; keep codes
   increasing across channels. Confirm availability before using the initial
   code `1`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` with at most
   500 characters. Review title, short description, full description, and privacy
   wording. For F-Droid, keep the short description under 80 characters and remove
   its current trailing period.
3. Run checks from the project root with JDK 17 and the Android SDK installed:

   ```sh
   just test
   just build
   ./gradlew lintRelease bundleRelease
   ```

   `just` selects JDK 17 automatically. For direct `./gradlew` commands, ensure
   JDK 17 is installed and discoverable; set `JAVA_HOME` if necessary.
4. Inspect the release package's permissions, identifier, version, and signature:

   ```sh
   "$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging app/build/outputs/apk/release/app-release.apk
   "$ANDROID_HOME/build-tools/36.0.0/aapt2" dump permissions app/build/outputs/apk/release/app-release.apk
   "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
   ```

   Set `ANDROID_HOME` to the local SDK directory first. These commands assume a
   signed build. Without signing, the filename is `app-release-unsigned.apk` and
   signature verification should fail. `just install` always installs the debug
   build, so it does **not** validate a release package.
5. Test a fresh release install and an upgrade from the previous release on a
   separate test device or emulator. Check usage permission onboarding, notification
   permission denial, goals, exclusions, collection, reminders, widgets, export,
   import, light/dark mode, and retained data. Do not uninstall a personal install
   just to work around a signature mismatch without exporting its data first.
6. Check Play's current [target Android requirements](https://support.google.com/googleplay/android-developer/answer/11926878).
   The project currently targets application programming interface (API) level 36.
   Validate [16-kilobyte memory page support](https://developer.android.com/guide/practices/page-sizes)
   on an appropriate emulator: dependencies include native libraries, so a Kotlin
   app is not automatically exempt. Check Play's report for the uploaded bundle.
7. Commit release changes with `git commit --signoff`. Tag the tested commit
   (for example, `git tag -a v0.1.0 -m 'Phone Sense 0.1.0'`) and push the commit
   and tag to the canonical repository. Never move a published release tag.
   Archive artifacts, their checksums, build-tool versions, and signing certificate
   fingerprints. Keep private credentials out of public release attachments.

## Google Play submission

1. Create the Phone Sense app in Play Console with the correct language, app
   category, free/paid choice, and support contact. Use the existing application
   identifier when uploading the first bundle.
2. Complete the store listing using the files under
   `fastlane/metadata/android/en-US/`. The repository stores metadata only; no
   automated store-upload workflow is configured. Supply a 512 × 512 icon, a
   1024 × 500 feature graphic, and at least two actual app screenshots. Check
   image formats and other limits against [Play preview asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151).
3. Complete App content declarations: privacy-policy link, ads, app access,
   target audience, content rating, Data safety, and any applicable permission
   declarations. Explain the usage-access setup for reviewers; there is no login.
   Assess the final build against [Data safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469).
   On-device processing alone is not off-device collection, but review backup,
   user-directed exports, and all dependencies before submitting answers.
4. Enroll in Play App Signing according to the chosen signing plan. Build with
   the configured upload key and upload
   `app/build/outputs/bundle/release/app-release.aab` to internal testing. Resolve
   bundle, signing, policy, and pre-launch report issues. Install through Play's
   test link to exercise the Play-delivered package.
5. If the account requires closed testing, complete it before applying for
   production access. For personal accounts created after November 13, 2023,
   the documented requirement is at least 12 testers continuously opted in for
   14 days. Internal testing does not replace this. Follow the
   [testing and production-access process](https://support.google.com/googleplay/android-developer/answer/14151465)
   and document feedback and fixes.
6. Choose distribution countries, add release notes, complete the production
   release, and submit for review. Once approved, publish according to the
   console's publishing controls. For updates, consider a staged rollout and
   monitor crashes, reviews, and data-migration reports.

## F-Droid submission

1. Review the [inclusion policy](https://f-droid.org/docs/Inclusion_Policy/).
   Confirm the public release tag contains all source, license notices, and
   build instructions. Audit transitive dependencies and their source availability;
   do not assume inclusion solely because the app has no network permission.
2. Follow the [submission guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/):
   fork `fdroiddata`, create a branch, and add
   `metadata/ca.mattmccormick.screenbudget.yml`. This recipe belongs in the
   F-Droid data repository, separately from this app's store metadata.
3. Use the [build metadata reference](https://f-droid.org/docs/Build_Metadata_Reference/)
   to supply the real source/issue addresses, license `GPL-3.0-or-later`, category,
   repository address, version, and immutable release commit. This project uses
   the `app` subdirectory and the unflavored Gradle release build (`gradle: [yes]`).
   Ensure the build environment provides JDK 17 and the required Android tools.
   Configure tag-based checks using `UpdateCheckMode: Tags` and
   `AutoUpdateMode: Version`; confirm detection with the actual version tags.
4. Build in a clean checkout without `keystore.properties` or signing environment
   variables. The ordinary F-Droid build must succeed unsigned without private
   credentials. Do not disable signing in your normal working checkout by
   deleting your only keystore. The existing reproducible archive settings are
   useful, but do not establish that the Android package is reproducible.
5. In your `fdroiddata` checkout, with `fdroidserver` installed, run:

   ```sh
   fdroid readmeta
   fdroid rewritemeta ca.mattmccormick.screenbudget
   fdroid lint ca.mattmccormick.screenbudget
   fdroid build ca.mattmccormick.screenbudget
   ```

   Test in F-Droid's supported build environment and review the fork's build
   pipeline. Fix scanner/build failures rather than broadly suppressing them.
6. If using developer signatures, publish the reference signed package at a
   stable release address and configure `Binaries` or a build's `binary`, plus
   `AllowedAPKSigningKeys`. Prove a match with F-Droid's rebuild before requesting
   publication. Ordinary F-Droid signing does not need this step.
7. Open a merge request to `fdroiddata`, respond to review, and monitor build and
   publication status. A merged recipe is not yet a published app. Confirm the
   listing and installation through the F-Droid client once available.

## Subsequent releases

Repeat the shared checks, increment versions, update release notes and affected
screenshots, and tag the tested source. Upload the new bundle to Play's test track
before production. Check that F-Droid detects the new tag and successfully builds
it; update its recipe when the toolchain or build steps change. If using reference
packages for reproducibility, publish one for every new release.

If a release has a serious problem, halt a Play staged rollout where available
and coordinate with F-Droid maintainers. Ship the fix with a higher version code;
do not replace a published tag or assume users can install a lower-version build.
