#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "usage: $0 AAPT2 APK" >&2
    exit 2
fi

aapt2=$1
apk=$2

if ! manifest_permissions=$("$aapt2" dump permissions "$apk"); then
    echo "FAIL: could not inspect permissions in $apk" >&2
    exit 1
fi

unexpected=()
declared=()
while IFS= read -r line; do
    [[ $line == uses-permission* ]] || continue

    if [[ $line =~ ^uses-permission(-sdk-[[:digit:]]+)?:[[:space:]]name=\'([^\']+)\' ]]; then
        permission=${BASH_REMATCH[2]}
    else
        unexpected+=("$line")
        continue
    fi

    declared+=("$permission")
    case "$permission" in
        android.permission.PACKAGE_USAGE_STATS|\
        android.permission.POST_NOTIFICATIONS|\
        android.permission.WAKE_LOCK|\
        android.permission.ACCESS_NETWORK_STATE|\
        android.permission.RECEIVE_BOOT_COMPLETED|\
        android.permission.FOREGROUND_SERVICE|\
        ca.mattmccormick.phone_sense.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION)
            ;;
        *) unexpected+=("$line") ;;
    esac
done <<< "$manifest_permissions"

if (( ${#unexpected[@]} )); then
    echo "FAIL: the merged manifest declares an unexpected permission:" >&2
    printf '     %s\n' "${unexpected[@]}" >&2
    exit 1
fi

echo "OK: the merged manifest declares only expected permissions."
printf '     expected: %s\n' "${declared[@]}"
