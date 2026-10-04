# Screen Budget

SPDX-License-Identifier: GPL-3.0-or-later

A local-only Android app that measures daily screen time per app, lets you
exclude apps that are not distractions, sets a weekly goal, and tells you each
morning how much time you have left. No account, no server, no INTERNET
permission. Data stays on the phone and can be exported.

Read the [privacy policy](PRIVACY.md) for details about local data storage and
permissions.

The plan is at `~/notes/20-29 Projects/Screen Budget/Plan.md`.

## Development

Install [just](https://just.systems/man/en/packages.html), JDK 17, and the Android
SDK (platform 36, build-tools 36.0.0, and platform-tools). Gradle-backed `just`
commands use `JAVA_HOME` when it points to JDK 17; otherwise they look for JDK 17
in standard Linux and macOS installation locations. If it cannot be found, install
JDK 17 or set `JAVA_HOME` to its installation directory. `ANDROID_HOME` defaults
to `~/Android/Sdk`; set it if your SDK is elsewhere.

Run `just --list` to see the available commands:

- `just build` builds the release APK (also the default for `just`). It is
  unsigned unless release signing is configured as described below.
- `just debug` builds the debug APK.
- `just test` runs the unit tests.
- `just emulator` builds the current checkout, launches the `Pixel_10a` virtual
  device, installs that build after Android boots, and opens the app. Pass another
  device name with `just emulator DEVICE_NAME`.
- `just wait-for-android` waits for a connected device to finish booting.
- `just install` builds and installs the debug APK on a connected device.
- `just launch` opens the installed app on a connected device.
- `just permissions` builds the debug APK and checks for permissions outside
  the app's own package.
- `just clean` removes build outputs.

Gradle remains the build system. The version-controlled
`gradle/gradle-daemon-jvm.properties` selects JDK 17 for the Gradle daemon,
including when running `./gradlew` directly. Install JDK 17 locally; automatic
downloads are not configured.

### Release signing

Create the release keystore once and keep it and its passwords private:

```sh
keytool -genkeypair -v -keystore release.jks -alias upload -keyalg RSA \
  -keysize 4096 -validity 10000
```

Create a gitignored `keystore.properties` in the repository root:

```properties
storeFile=release.jks
storePassword=your-keystore-password
keyAlias=upload
keyPassword=your-key-password
```

`./gradlew assembleRelease` then signs the APK with that key. CI can provide the
same values through `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and
`KEY_PASSWORD`; environment variables fill in any values absent from the
properties file. With neither source configured, the release build succeeds and
clearly reports that its APK is unsigned, as required for F-Droid builds.

### Android Studio

Android Studio versions that support Gradle daemon JVM criteria use the same
Java 17 setting when importing the project. New worktrees inherit this setting
once their branch contains `gradle/gradle-daemon-jvm.properties`.

- **Run the full app without a phone:** Create a virtual Android device in Android Studio’s **Device Manager**, select it, and click **Run**. This installs into the emulator; usage data comes from that virtual device, not your phone or computer. [Android Emulator](<https://developer.android.com/studio/run/emulator>)

#### Clear emulator app storage

To reset saved navigation state and all Screen Budget data, open the emulator's
**Settings → Apps → Screen Budget → Storage & cache → Clear storage**. This also
removes stored goals and settings and causes onboarding to appear again.

The equivalent Android Debug Bridge command is:

```sh
adb shell pm clear ca.mattmccormick.screenbudget
```
