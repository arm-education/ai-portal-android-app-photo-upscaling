# Tests

Run these commands from the repository root. Prepare the Python environment in the
[application README](../README.md#download-a-model) and connect an authorized
Arm-based Android device with the `arm64-v8a` ABI. Download access is required to
prepare the test models.

## Native Android integration tests

The tests use a separate instrumentation APK with the three pinned model programs.
Model weights are never included in the application APK and are ignored by Git.

On macOS or Linux:

```bash
.hf-venv/bin/python tests/prepare_models.py
./gradlew :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
```

On Windows PowerShell:

```powershell
.\.hf-venv\Scripts\python.exe tests/prepare_models.py
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
```

Install both APKs and run the tests:

```console
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w org.arm.learningpath.upscaling.test/org.arm.learningpath.upscaling.ContractInstrumentation
```

Success prints twelve dimension cases, import/switching/PNG checks, and a
multi-touch detail-view check. A failure prints `FAIL` and a stack trace. This
custom runner does not print a JUnit summary. Test imports use file URIs; also
check the document pickers and save workflow manually using the README steps.

If more than one device is connected, add `-s DEVICE_SERIAL` after `adb` in each
command to select the intended device.

## Tile-coordinate tests

These tests require only JDK 17 or later:

```console
javac -d build/tile-tests app/src/main/java/org/arm/learningpath/upscaling/TileMath.java tests/TileMathTest.java
java -cp build/tile-tests org.arm.learningpath.upscaling.TileMathTest
```
