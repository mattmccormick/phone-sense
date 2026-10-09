android_home := env_var_or_default("ANDROID_HOME", env_var("HOME") / "Android/Sdk")
build_tools := android_home / "build-tools/36.0.0"
application_id := "ca.mattmccormick.phone_sense"
debug_apk := "app/build/outputs/apk/debug/app-debug.apk"

# Build the release APK.
build: (_gradle "assembleRelease")

# Create and push an annotated release tag from app/build.gradle.kts's versionName.
tag:
    #!/usr/bin/env bash
    set -euo pipefail

    if [[ -n "$(git status --porcelain)" ]]; then
        echo "error: working tree must be clean before tagging" >&2
        exit 1
    fi

    mapfile -t versions < <(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' app/build.gradle.kts)
    if [[ ${#versions[@]} -ne 1 || -z "${versions[0]}" ]]; then
        echo "error: expected exactly one static versionName in app/build.gradle.kts" >&2
        exit 1
    fi

    tag="v${versions[0]}"
    git check-ref-format "refs/tags/$tag"
    if git show-ref --verify --quiet "refs/tags/$tag"; then
        echo "error: tag $tag already exists" >&2
        exit 1
    fi

    git tag -a "$tag" -m "Phone Sense $tag"
    git push origin "$tag"
    printf 'Created tag %s at %s\n' "$tag" "$(git rev-parse --short HEAD)"

# Build the debug APK; this is the one `install` and default `permissions` use.
debug: (_gradle "assembleDebug")

# Run the unit tests.
test: test-permissions (_gradle "test")

# Test the APK permission allowlist and its failure behavior.
test-permissions:
    @bash scripts/test-check-apk-permissions.sh

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

# Fail if an APK declares a permission outside the exact allowlist. With no
# argument, build and check the debug APK. Pass a path to check a built release.
#
permissions package="":
    #!/usr/bin/env bash
    set -euo pipefail

    package={{quote(package)}}
    if [[ -z "$package" ]]; then
        {{quote(just_executable())}} -- debug
        package={{quote(debug_apk)}}
    fi

    scripts/check-apk-permissions.sh "{{build_tools}}/aapt2" "$package"

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
