# Building and installing

## Verified toolchain target

Unity 6000.6.0f1 with Android Build Support provides Java 17, Gradle 9.1.0, AGP 9.0.0, Android SDK 36 and NDK. The native module targets Java 17, minimum Android API 26, compile/target API 36, Kotlin 2.4.10. The demo uses IL2CPP and ARM64, the standard Unity Activity, and a separate AndroidX wallet host.

The official 0.2.0 documentation requires compile SDK 36+, AGP 8.9.1+, and Kotlin 2+. Those are minimums, not a statement that every such combination is tested here. The published dependency graph includes newer AndroidX/coroutines artifacts. Do not downgrade them or suppress Kotlin metadata checks.

## Standalone Kotlin library

Windows, from the repository root:

```powershell
./scripts/build-native.ps1 -UnityEditor 'C:/Program Files/Unity/Hub/Editor/6000.6.0f1/Editor/Unity.exe'
```

Or provide Java 17 and Android SDK 36 yourself:

```sh
cd NativeAndroid
# Set JAVA_HOME and ANDROID_HOME to your installed toolchains first.
./gradlew :seekerBridge:assembleRelease :seekerBridge:testDebugUnitTest
```

On Unix, use `sh gradlew` if the wrapper executable bit is absent. The wrapper distribution has a pinned SHA-256 checksum. Output: `Packages/com.community.seekerconnect.unity/Native~/build/outputs/aar/seekerBridge-release.aar`.

**Do not drop the AAR alone into another project.** It does not embed Maven transitive dependencies. The supported UPM installation compiles the native module in the Unity Gradle build and resolves all dependencies together. No AAR needs to be checked into Git.

## Unity build

Open the committed scene, then select **Seeker Connect → Build Android APK**. The menu configures ARM64/IL2CPP, SDK 26/36, portrait orientation, app identifier `com.community.seekerconnect.demo`, and a development APK signed with the local Android debug key. Use your own package ID and securely managed release signing for distribution.

Headless with the Unity CLI:

```sh
unity run . --timeout 1800 -- -buildTarget Android -executeMethod DemoBuilder.BuildApk
unity test . --mode EditMode --output Logs/editmode.xml
```

You may also invoke the installed Unity executable with `-batchmode -quit -projectPath <root> -buildTarget Android -executeMethod DemoBuilder.BuildApk -logFile <log>`; omit `-quit` for Unity Test Runner invocations.

The editor hook copies the native module into the generated root, adds `include ':seekerBridge'`, adds `implementation project(':seekerBridge')` to `unityLibrary`, and declares the pinned Kotlin plugin in the root plugin block. The standard Unity repositories include Google Maven and Maven Central. `android.useAndroidX=true` is added. AGP 9.0's built-in Kotlin is disabled in favor of the explicitly versioned Kotlin plugin, with the compatible old DSL flag; AGP emits deprecation warnings for this supported 9.0 migration mode. Do not upgrade to AGP 10 without migrating the integration.

The hook also disables app backup and excludes SharedPreferences from cloud/device-transfer backup. This affects all host-app SharedPreferences, deliberately protecting the official authorization cache. If your app needs other backup data, merge narrowly scoped exclusions for your cache namespace instead of removing the protection.

## Export route / older Unity 6 LTS

Prefer the recorded editor. An older LTS installation is not verified by this repository. Configure its custom base Gradle template to AGP 9.0.0 and external Gradle to 9.1.0; install SDK 36 and Java 17. Additional Unity-version-specific template migrations may be needed. The integration fails explicitly when its root AGP check is not satisfied.

With the recorded editor, **Seeker Connect → Export Android Gradle Project** produces `Builds/AndroidExport`. Build this source tree separately with the pinned wrapper:

```powershell
$env:JAVA_HOME = '<Java 17 installation>'
$env:ANDROID_HOME = '<Android SDK installation>'
./NativeAndroid/gradlew.bat -p Builds/AndroidExport :launcher:assembleDebug
```

This route keeps the entire dependency graph and manifest merging visible. Unity still needs a valid license and its Android/IL2CPP module to export native player binaries.

## App identity and icon

Own and operate an HTTPS website. Upload a suitable square app icon, for example `images/icon.png`, and verify it can be fetched publicly over HTTPS. Set `identityUri` to the app website and `iconPath` to that relative icon path. Do not use a custom scheme, credentials in the URI, another company's identity, or a placeholder in a shipped app. The demo lets you enter this at runtime; a game should supply its deployment configuration in code.

Authorization caches are namespaced by a SHA-256 of identity URI, chain, and Android wallet policy. Certified-only and mock-testing namespaces remain compatible with previous versions; production generic MWA uses a separate namespace. Changing identity, chain, or policy does not reuse the previous authorization. Initialize once per app process and use Disconnect to clear that configuration's cache. See [Android wallet policy](ANDROID_WALLETS.md).

## Installing only the package

Install the Git URL shown in README, or use a local package. Use the compatible Android toolchain above and configure your own scene, IL2CPP, ARM64, minimum SDK 26 and target SDK 36. The package's runtime does not depend on the demo, RPC helper, or UI Toolkit. The package's `Native~` directory is intentionally hidden from Unity's asset importer while remaining accessible to the build hook.
