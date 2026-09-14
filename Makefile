ANDROID_HOME ?= $(HOME)/Android/Sdk
JAVA_HOME := /usr/lib/jvm/java-17-openjdk-amd64
BUILD_TOOLS := $(ANDROID_HOME)/build-tools/36.0.0
APPLICATION_ID := ca.mattmccormick.screenbudget
APK := app/build/outputs/apk/release/app-release-unsigned.apk
DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk

.PHONY: build debug test install permissions clean

# Build the release APK.
build:
	JAVA_HOME=$(JAVA_HOME) ./gradlew assembleRelease

# Build the debug APK; this is the one `install` and `permissions` use.
debug:
	JAVA_HOME=$(JAVA_HOME) ./gradlew assembleDebug

# Run the unit tests.
test:
	JAVA_HOME=$(JAVA_HOME) ./gradlew test

# Install the debug APK on a phone connected via USB (debugging
# enabled/authorized). The release APK is unsigned and will not install.
install: debug
	$(ANDROID_HOME)/platform-tools/adb install -r $(DEBUG_APK)

# Fail if the built APK declares a permission from outside the app's own
# package. Screen Budget asks for no platform permission until a ticket adds
# one deliberately, so this guards the manifest merge against a permission
# arriving from a library.
#
# androidx.core merges in a signature permission named after the application
# id (DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION) so ContextCompat can register
# non-exported receivers. It is the app's own permission, held by this app
# alone, and it grants no access to the device or the network, so the check
# reports it and lets it pass. Anything else fails the target.
permissions: debug
	@$(BUILD_TOOLS)/aapt2 dump permissions $(DEBUG_APK) \
		| grep "^uses-permission" \
		| grep -v "name='$(APPLICATION_ID)\." > /tmp/screen-budget-permissions.txt \
		|| true
	@if [ -s /tmp/screen-budget-permissions.txt ]; then \
		echo "FAIL: the merged manifest declares a permission:"; \
		cat /tmp/screen-budget-permissions.txt; \
		exit 1; \
	fi
	@echo "OK: the merged manifest declares no permission outside $(APPLICATION_ID)."
	@$(BUILD_TOOLS)/aapt2 dump permissions $(DEBUG_APK) \
		| grep "^uses-permission" \
		| sed 's/^/     ignored, app-private: /' || true

clean:
	JAVA_HOME=$(JAVA_HOME) ./gradlew clean
