#!/usr/bin/env bash
set -euo pipefail

project_dir=$(cd "$(dirname "$0")/.." && pwd)
checker="$project_dir/scripts/check-apk-permissions.sh"
test_dir=$(mktemp -d "${TMPDIR:-/tmp}/phone-sense-permissions-test.XXXXXX")
trap 'rm -rf "$test_dir"' EXIT

cat > "$test_dir/aapt2" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
[[ $1 == dump && $2 == permissions ]]
case "$3" in
    allowed.apk)
        cat <<'OUTPUT'
package: ca.mattmccormick.phone_sense
uses-permission: name='android.permission.PACKAGE_USAGE_STATS'
uses-permission: name='android.permission.POST_NOTIFICATIONS'
uses-permission: name='android.permission.WAKE_LOCK'
uses-permission: name='android.permission.ACCESS_NETWORK_STATE'
uses-permission: name='android.permission.RECEIVE_BOOT_COMPLETED'
uses-permission: name='android.permission.FOREGROUND_SERVICE'
uses-permission: name='ca.mattmccormick.phone_sense.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
OUTPUT
        ;;
    internet.apk) echo "uses-permission: name='android.permission.INTERNET'" ;;
    app-owned.apk) echo "uses-permission: name='ca.mattmccormick.phone_sense.NOT_ALLOWED'" ;;
    sdk-internet.apk) echo "uses-permission-sdk-23: name='android.permission.INTERNET'" ;;
    malformed.apk) echo "uses-permission: malformed" ;;
    bad.apk) exit 1 ;;
    *) exit 2 ;;
esac
MOCK
chmod +x "$test_dir/aapt2"

assert_passes() {
    "$checker" "$test_dir/aapt2" "$1" > /dev/null
}

assert_fails() {
    if "$checker" "$test_dir/aapt2" "$1" > /dev/null 2>&1; then
        echo "expected permission check to fail for $1" >&2
        exit 1
    fi
}

assert_passes allowed.apk
assert_fails internet.apk
assert_fails app-owned.apk
assert_fails sdk-internet.apk
assert_fails malformed.apk
assert_fails bad.apk

# Each invocation owns its output, so overlapping checks cannot mask failures.
assert_passes allowed.apk &
first_pid=$!
assert_fails internet.apk &
second_pid=$!
wait "$first_pid"
wait "$second_pid"

echo "Permission checker tests passed."
