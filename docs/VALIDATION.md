# Validation record

Date: **2026-10-25 (Asia/Karachi)**. Platform: Windows x64.

The table and APK hash below record the original 0.1 build. The updated 0.1.1 demo enables explicit Devnet MWA testing; its follow-up record appears at the end of this document.

| Check | Result |
| --- | --- |
| Unity editor | Installed **6000.6.0f1** (Unity 6.6; not an LTS editor) |
| Official Seeker Connect | **0.2.0**, resolved from Maven Central |
| Native release AAR | **Passed**, `:seekerBridge:assembleRelease` |
| Native automated tests | **16 passed**, 0 failed: wire validation 9, Android lifecycle/dispatch 5, official cache 2 |
| Unity compilation | **Passed**, including Android-only JNI/editor integration code |
| Unity Edit Mode tests | **9 passed**, 0 failed |
| Unity Play Mode tests | **2 passed**, 0 failed; loaded scene panel/controls and explicit unsupported Editor wallet behavior |
| Actual UI rendering | **Inspected**, portrait Play-mode capture in `images/editor-preview.png` |
| Final Android APK | **Built successfully**, ARM64 IL2CPP development APK |
| APK signing | **Verified**, APK Signature Scheme v2; local development key, not a distribution signing identity |
| SDK levels | APK reports minimum **26**, target/compile **36** |
| Manifest merge | **Verified**: original UnityPlayerActivity launcher plus non-exported WalletHostActivity, wallet queries, INTERNET permission and backup exclusions |
| Dependencies | Native/Unity Gradle builds resolved and packaged AndroidX, Seeker Connect and its transitive dependencies |
| Static JNI boundary | `javap` confirmed static `request(Activity,String,String,String,String)` and `cancel(String)`; corresponding bridge classes are in the APK DEX |
| Async delivery | Request IDs, callback envelopes, duplicate/late routing, cancellation and errors covered by automated tests; live Android → Unity callback delivery **not device-verified** |
| Android device installation | **Not performed**: `adb devices` returned no connected devices |
| Real Seeker authorization/signing | **Not performed**: no Seeker/wallet available |
| Mainnet transfers | **Not performed**; demo builds a Devnet memo and never broadcasts |

Final local APK: `Builds/SeekerConnectDemo.apk`.

SHA-256:

```text
4d72717052437480d5ca208c88719b891b139409c3ab385fe26c3835aafa7c3b
```

Build binaries, debug keys, Unity caches and Gradle/CMake caches are deliberately excluded from Git. The complete source, native Gradle wrapper, package lock, project settings, scene and `.meta` files are included. Locally generated logs/test reports are under `Logs` and `Packages/com.community.seekerconnect.unity/Native~/build/test-results`.

## Reproduce

```powershell
./scripts/build-native.ps1
unity test . --mode EditMode --output Logs/editmode.xml
unity test . --mode PlayMode --output Logs/playmode.xml
unity run . --timeout 1800 -- -buildTarget Android -executeMethod DemoBuilder.BuildApk
./scripts/verify-apk.ps1
```

The first native build resolves external Maven dependencies; the first IL2CPP build compiles the player and takes longer. The pinned AGP 9.0 build emits deprecation warnings for the external Kotlin plugin/old DSL migration flags. SDK scanning can warn about read-only metadata under Program Files. These did not prevent the recorded successful builds. A Unity Editor Search indexing exception appeared during the separate batch preview capture; the UI rendered and the automated Play Mode suite passed.

## Remaining verification and limits

- Complete the [Seeker device checklist](TESTING.md) before treating wallet behavior as production verified. Real approval/rejection, app switching, device process recreation, and cryptographic signature checks remain unverified on hardware.
- Supply your own HTTPS identity and hosted icon for your app. The updated demo uses this project's GitHub identity.
- Only Unity 6000.6.0f1/toolchain recorded here was built. Older Unity 6 LTS versions, other operating systems, and production/minified builds are not yet validated.
- Certified availability is required by default. The updated demo explicitly enables Devnet-only generic MWA testing; other wallet integrations remain unverified.
- Restore means cached authorization; the wallet revalidates it during signing. Disconnect clears local cache, not the wallet's grant.
- Sign-and-send exposes submission only and was not exercised with a wallet. Callers must confirm via RPC and reconcile ambiguous failures before any retry.
- Android backup protection applies to all host-app SharedPreferences. Integrators with existing backup policies should retain equivalent token exclusions.

This is a source-complete integration with verified local compilation/builds and the tests described above, not a claim that a real wallet flow was exercised.

## Devnet MWA testing update — 0.1.1

The demo enables `allowMwaTesting` to exercise the same official Seeker Connect SDK through its generic MWA fallback on ordinary Android devices. Testing authorizations use a separate cache namespace; the option rejects non-Devnet chains in both C# and Kotlin.

- Native tests: 21 passed; Unity Edit Mode: 12 passed; Unity Play Mode: 2 passed (35 total).
- Android IL2CPP ARM64 build and APK signature/content verification passed.
- APK version 0.1.1, version code 2, installed successfully on Samsung SM-S918B using `adb install -r`.
- Existing Mock MWA Wallet version 1.0.1 retained.
- APK SHA-256: `2b86f6edadcde504b1344ec445ee5706871e31a19a82083f75b498989914eb08`.

Wallet approval, signing, and session restoration still require the interactive device checklist. Certified Seeker routing requires a physical Seeker; the Samsung fallback does not validate that route.
