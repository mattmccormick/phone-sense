android_home := env_var_or_default("ANDROID_HOME", env_var("HOME") / "Android/Sdk")
build_tools := android_home / "build-tools/36.0.0"
application_id := "ca.mattmccormick.screenbudget"
debug_apk := "app/build/outputs/apk/debug/app-debug.apk"

# Build the release APK.
build: (_gradle "assembleRelease")

# Build the debug APK; this is the one `install` and `permissions` use.
debug: (_gradle "assembleDebug")

# Run the unit tests.
test: (_gradle "test")

# Wait until a connected physical Android device has finished booting.
wait-for-android target="-d":
    #!/usr/bin/env bash
    set -euo pipefail

    adb_bin="{{android_home}}/platform-tools/adb"
    "$adb_bin" {{quote(target)}} wait-for-device
    boot_completed=""
    until [[ "${boot_completed//$'\r'/}" == "1" ]]; do
        boot_completed="$("$adb_bin" {{quote(target)}} shell getprop sys.boot_completed)"
        sleep 1
    done

# Open Phone Sense on a connected physical Android device.
launch target="-d":
    "{{android_home}}/platform-tools/adb" {{quote(target)}} shell am start \
        -n {{application_id}}/.MainActivity

# Build this worktree, start a virtual device, install the app, and open it.
emulator device="Pixel_10a": debug
    #!/usr/bin/env bash
    set -euo pipefail

    emulator_bin="{{android_home}}/emulator/emulator"
    just_bin={{quote(just_executable())}}
    "$emulator_bin" -avd {{quote(device)}} &
    emulator_pid=$!
    trap 'kill "$emulator_pid" 2>/dev/null || true' EXIT INT TERM

    "$just_bin" -- wait-for-android -e
    "$just_bin" -- install -e
    wait "$emulator_pid"

# The release APK is unsigned and will not install.
# Build and install the debug APK, then open it on a connected physical Android device.
install target="-d": debug
    "{{android_home}}/platform-tools/adb" {{quote(target)}} install -r {{debug_apk}}
    {{quote(just_executable())}} -- launch {{quote(target)}}

# Fail if the built APK declares an unexpected permission. Phone Sense needs
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
clean: (_gradle "clean")

# Run Gradle with JDK 17, preferring JAVA_HOME when it is already compatible.
[private]
_gradle *args:
    #!/usr/bin/env bash
    set -euo pipefail

    is_jdk_17() {
        local home="$1"
        [[ -x "$home/bin/java" && -x "$home/bin/javac" ]] || return 1
        "$home/bin/java" -XshowSettings:properties -version 2>&1 \
            | grep -Eq '^ *java\.specification\.version = 17$'
    }

    candidates=()
    [[ -z "${JAVA_HOME:-}" ]] || candidates+=("$JAVA_HOME")

    if [[ "$(uname -s)" == "Darwin" ]] && [[ -x /usr/libexec/java_home ]]; then
        mac_home="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
        [[ -z "$mac_home" ]] || candidates+=("$mac_home")
    fi

    for home in \
        /usr/lib/jvm/java-17-openjdk-amd64 \
        /usr/lib/jvm/java-17-openjdk-* \
        /usr/lib/jvm/jdk-17* \
        /usr/java/jdk-17* \
        /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
        /usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
        /Library/Java/JavaVirtualMachines/*17*/Contents/Home; do
        [[ -d "$home" ]] && candidates+=("$home")
    done

    for home in "${candidates[@]}"; do
        if is_jdk_17 "$home"; then
            export JAVA_HOME="$home"
            export PATH="$JAVA_HOME/bin:$PATH"
            exec ./gradlew "{{args}}"
        fi
    done

    echo "error: JDK 17 was not found. Install JDK 17 or set JAVA_HOME to its installation directory." >&2
    exit 1
