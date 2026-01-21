# Bitchat Android - Makefile
# Key commands for building, testing, and managing the project

.PHONY: help build clean install test lint run stop logs device-info

# Default target
help:
	@echo "Bitchat Android - Available Commands:"
	@echo ""
	@echo "Building & Installation:"
	@echo "  build          - Build debug APK"
	@echo "  build-release  - Build release APK"
	@echo "  clean          - Clean build artifacts"
	@echo "  install        - Install debug APK to connected device"
	@echo "  install-release- Install release APK to connected device"
	@echo "  uninstall      - Uninstall app from device"
	@echo ""
	@echo "Development:"
	@echo "  compile        - Compile Kotlin code only (faster)"
	@echo "  lint           - Run lint checks"
	@echo "  test           - Run unit tests"
	@echo "  test-ui        - Run instrumented UI tests"
	@echo ""
	@echo "Device Management:"
	@echo "  run            - Launch app on device"
	@echo "  stop           - Force stop app on device"
	@echo "  logs           - Show app logs (logcat)"
	@echo "  device-info    - Show connected device info"
	@echo "  permissions    - Grant required permissions"
	@echo ""
	@echo "Utilities:"
	@echo "  deps           - Show dependency tree"
	@echo "  size           - Show APK size analysis"
	@echo "  gradle-clean   - Clean Gradle cache"

# Building
build:
	@echo "Building debug APK..."
	./gradlew assembleDebug

build-release:
	@echo "Building release APK..."
	./gradlew assembleRelease

clean:
	@echo "Cleaning build artifacts..."
	./gradlew clean

compile:
	@echo "Compiling Kotlin code..."
	./gradlew compileDebugKotlin

# Installation
install: build
	@echo "Installing debug APK..."
	./gradlew installDebug

install-release: build-release
	@echo "Installing release APK..."
	./gradlew installRelease

uninstall:
	@echo "Uninstalling app..."
	adb uninstall com.bitchat.droid || echo "App not installed"

# Testing
test:
	@echo "Running unit tests..."
	./gradlew test

test-ui:
	@echo "Running instrumented tests..."
	./gradlew connectedAndroidTest

lint:
	@echo "Running lint checks..."
	./gradlew lint

# Device Management
run:
	@echo "Launching Bitchat..."
	adb shell am start -n com.bitchat.droid/com.bitchat.android.MainActivity

stop:
	@echo "Stopping Bitchat..."
	adb shell am force-stop com.bitchat.droid

logs:
	@echo "Showing app logs (Ctrl+C to stop)..."
	adb logcat | grep -E "(bitchat|Bitchat|BITCHAT)"

device-info:
	@echo "Connected device information:"
	@adb devices -l
	@echo ""
	@echo "Device properties:"
	@adb shell getprop ro.product.model
	@adb shell getprop ro.build.version.release

permissions:
	@echo "Granting required permissions..."
	adb shell pm grant com.bitchat.droid android.permission.ACCESS_FINE_LOCATION
	adb shell pm grant com.bitchat.droid android.permission.ACCESS_COARSE_LOCATION
	adb shell pm grant com.bitchat.droid android.permission.BLUETOOTH_SCAN
	adb shell pm grant com.bitchat.droid android.permission.BLUETOOTH_CONNECT
	adb shell pm grant com.bitchat.droid android.permission.BLUETOOTH_ADVERTISE
	adb shell pm grant com.bitchat.droid android.permission.POST_NOTIFICATIONS
	adb shell pm grant com.bitchat.droid android.permission.RECORD_AUDIO
	adb shell pm grant com.bitchat.droid android.permission.READ_MEDIA_AUDIO
	@echo "Permissions granted!"

# Development Workflow Shortcuts
dev: clean compile install run
	@echo "Development build complete and running!"

quick: compile install
	@echo "Quick build and install complete!"

# Utilities
deps:
	@echo "Showing dependency tree..."
	./gradlew app:dependencies

size:
	@echo "APK size analysis..."
	./gradlew app:analyzeDebugBundle || echo "Bundle analysis not available, showing APK info:"
	@ls -lh app/build/outputs/apk/debug/*.apk 2>/dev/null || echo "No debug APK found"

gradle-clean:
	@echo "Cleaning Gradle cache..."
	./gradlew --stop
	rm -rf ~/.gradle/caches/
	rm -rf .gradle/

# Music Analytics Specific Commands
music-test:
	@echo "Running music analytics tests..."
	./gradlew test --tests "*music*" --tests "*Music*"

music-logs:
	@echo "Showing music-related logs..."
	adb logcat | grep -E "(Music|Analytics|Transfer|Player)"

# Release Management
release-check: lint test
	@echo "Running pre-release checks..."
	./gradlew assembleRelease
	@echo "Release checks complete!"

# Debugging
debug-info:
	@echo "Debug information:"
	@echo "Package: com.bitchat.droid"
	@echo "Main Activity: com.bitchat.android.MainActivity"
	@echo ""
	@echo "App status:"
	@adb shell ps | grep bitchat || echo "App not running"
	@echo ""
	@echo "Recent crashes:"
	@adb shell dumpsys dropbox --print | grep bitchat | tail -5 || echo "No recent crashes"

# Network & Mesh Debugging
mesh-debug:
	@echo "Mesh network debugging..."
	adb logcat | grep -E "(Mesh|Bluetooth|BLE|Peer|Connection)"

# File Transfer Debugging  
transfer-debug:
	@echo "Transfer system debugging..."
	adb logcat | grep -E "(Transfer|Share|File|Library)"

# Performance Monitoring
perf:
	@echo "Performance monitoring..."
	adb shell top -n 1 | grep bitchat || echo "App not running"
	@echo ""
	@echo "Memory usage:"
	@adb shell dumpsys meminfo com.bitchat.droid | head -20 || echo "App not running"

# Backup & Restore (for development)
backup-prefs:
	@echo "Backing up app preferences..."
	adb shell run-as com.bitchat.droid cp -r /data/data/com.bitchat.droid/shared_prefs /sdcard/bitchat_backup/ 2>/dev/null || echo "Backup failed - check permissions"

# Clean everything (nuclear option)
nuke: clean gradle-clean uninstall
	@echo "Everything cleaned! Ready for fresh start."

# Quick development cycle
cycle: stop clean compile install run logs
	@echo "Full development cycle complete!"