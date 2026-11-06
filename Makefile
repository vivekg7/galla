# One entrypoint, so humans and agents run the same commands.

PRETTIER ?= npx --yes --prefer-offline prettier@latest

# Gradle needs a JDK. A Mac with only Android Studio has one inside the app.
STUDIO_JBR := /Applications/Android Studio.app/Contents/jbr/Contents/Home
export JAVA_HOME ?= $(shell [ -d "$(STUDIO_JBR)" ] && echo "$(STUDIO_JBR)")
GRADLE = ./gradlew -q

.PHONY: help fmt lint test test-device check debug install release bundle

help:
	@echo 'fmt      rewrite files: ktlint on Kotlin, prettier on Markdown -- the only target that edits anything'
	@echo 'lint     ktlint, Android lint, prettier --check'
	@echo 'test     the JVM unit tests'
	@echo 'test-device  migration and sync tests on ANDROID_SERIAL; uninstalls the debug app after, so use an emulator'
	@echo 'check    lint test'
	@echo 'debug    build the debug APK (io.github.vivekg7.galla.debug, beside a release install)'
	@echo 'install  build the debug APK and install it on the phone connected over adb'
	@echo 'release  build the release APK (signed if local/keystore.properties exists), to try the minified build'
	@echo 'bundle   build the release app bundle to upload to Play (docs/play-store.md)'

fmt:
	$(GRADLE) ktlintFormat
	$(PRETTIER) --write '**/*.md'

lint:
	$(GRADLE) ktlintCheck :app:lintDebug
	$(PRETTIER) --check '**/*.md'

test:
	$(GRADLE) :app:testDebugUnitTest

check: lint test

# Room and SQLite run only on Android. Gradle uninstalls the app it tested,
# data and all: never point this at the shop's phone.
test-device:
	@[ -n "$$ANDROID_SERIAL" ] || { echo 'set ANDROID_SERIAL to an emulator, e.g. emulator-5554'; exit 1; }
	$(GRADLE) :app:connectedDebugAndroidTest

debug:
	$(GRADLE) :app:assembleDebug

install:
	$(GRADLE) :app:installDebug

release:
	$(GRADLE) :app:assembleRelease
	@ls -l app/build/outputs/apk/release/

# What Play takes. Play re-signs it with its own key; ours only proves the
# upload is ours, so it must be signed (local/keystore.properties).
bundle:
	@[ -f local/keystore.properties ] || { echo 'needs local/keystore.properties: docs/play-store.md'; exit 1; }
	$(GRADLE) :app:bundleRelease
	@ls -l app/build/outputs/bundle/release/
