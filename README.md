# Screen Budget

A local-only Android app that measures daily screen time per app, lets you
exclude apps that are not distractions, sets a weekly goal, and tells you each
morning how much time you have left. No account, no server, no INTERNET
permission. Data stays on the phone and can be exported.

The plan is at `~/notes/20-29 Projects/Screen Budget/Plan.md`.

## Development

Install [just](https://just.systems/man/en/packages.html), JDK 17, and the Android
SDK (platform 36, build-tools 36.0.0, and platform-tools). Commands use `JAVA_HOME`
when set, otherwise Java from `PATH`. Set `JAVA_HOME` to your JDK 17 installation
if needed. `ANDROID_HOME` defaults to `~/Android/Sdk`; set it if your SDK is
elsewhere.

Run `just --list` to see the available commands:

- `just build` builds the unsigned release APK (also the default for `just`).
- `just debug` builds the debug APK.
- `just test` runs the unit tests.
- `just install` builds and installs the debug APK on a USB-connected phone
  with USB debugging enabled and authorized.
- `just permissions` builds the debug APK and checks for permissions outside
  the app's own package.
- `just clean` removes build outputs.

Gradle remains the build system; you can also run `./gradlew` commands directly.
