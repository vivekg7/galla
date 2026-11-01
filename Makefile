# One entrypoint, so humans and agents run the same commands.

PRETTIER ?= npx --yes --prefer-offline prettier@latest

# Gradle needs a JDK. A Mac with only Android Studio has one inside the app.
STUDIO_JBR := /Applications/Android Studio.app/Contents/jbr/Contents/Home
export JAVA_HOME ?= $(shell [ -d "$(STUDIO_JBR)" ] && echo "$(STUDIO_JBR)")
GRADLE = ./gradlew -q

.PHONY: help fmt lint test check debug install release

help:
	@echo 'fmt      rewrite files: ktlint on Kotlin, prettier on Markdown -- the only target that edits anything'
	@echo 'lint     ktlint, Android lint, prettier --check'
	@echo 'test     the JVM unit tests'
	@echo 'check    lint test'
	@echo 'debug    build the debug APK (io.github.vivekg7.galla.debug, beside a release install)'
	@echo 'install  build the debug APK and install it on the phone connected over adb'
	@echo 'release  build the release APK (signed if local/keystore.properties exists)'

fmt:
	$(GRADLE) ktlintFormat
	$(PRETTIER) --write '**/*.md'

lint:
	$(GRADLE) ktlintCheck :app:lintDebug
	$(PRETTIER) --check '**/*.md'

test:
	$(GRADLE) :app:testDebugUnitTest

check: lint test

debug:
	$(GRADLE) :app:assembleDebug

install:
	$(GRADLE) :app:installDebug

release:
	$(GRADLE) :app:assembleRelease
	@ls -l app/build/outputs/apk/release/
