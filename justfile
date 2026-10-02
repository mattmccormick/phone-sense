android_home := env_var_or_default("ANDROID_HOME", env_var("HOME") / "Android/Sdk")
build_tools := android_home / "build-tools/36.0.0"
application_id := "ca.mattmccormick.screenbudget"
debug_apk := "app/build/outputs/apk/debug/app-debug.apk"

# Build the release APK.
build:
    ./gradlew assembleRelease

# Build the debug APK; this is the one `install` and `permissions` use.
debug:
    ./gradlew assembleDebug

# Run the unit tests.
test:
    ./gradlew test

# Install the debug APK on a phone connected via USB (debugging
# enabled/authorized). The release APK is unsigned and will not install.
# Build and install the debug APK on an authorized USB device.
install: debug
    "{{android_home}}/platform-tools/adb" install -r {{debug_apk}}

# Fail if the built APK declares an unexpected permission. Screen Budget needs
# usage access for collection; this guards the manifest merge against another
# permission arriving from a library.
#
# androidx.core merges in a signature permission named after the application
# id (DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION) so ContextCompat can register
# non-exported receivers. It is the app's own permission, held by this app
# alone, and it grants no access to the device or the network, so the check
# reports it and lets it pass. Anything else fails the target.
# Check the debug APK for permissions outside the app package.
permissions: debug
    @"{{build_tools}}/aapt2" dump permissions {{debug_apk}} \
        | grep "^uses-permission" \
        | grep -v "name='android.permission.PACKAGE_USAGE_STATS'" \
        | grep -v "name='{{application_id}}\." > /tmp/screen-budget-permissions.txt \
        || true
    @if [ -s /tmp/screen-budget-permissions.txt ]; then \
        echo "FAIL: the merged manifest declares a permission:"; \
        cat /tmp/screen-budget-permissions.txt; \
        exit 1; \
    fi
    @echo "OK: the merged manifest declares only expected permissions."
    @"{{build_tools}}/aapt2" dump permissions {{debug_apk}} \
        | grep "^uses-permission" \
        | sed 's/^/     expected: /' || true

# Remove build outputs.
clean:
    ./gradlew clean
