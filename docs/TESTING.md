# Tests and troubleshooting

## Automated checks

Run `scripts/build-native.ps1` (Windows) or the Gradle commands in BUILDING.md. Native unit tests cover signed-message encoding, transaction wire validation, Android callback envelopes, absence of a certified wallet, busy/cancellation behavior, host recreation without replay, and official cache restoration/clearing across client instances. Robolectric supplies an Android test environment; a test-only Unity callback sink and explicit cache fixtures are never shipped in the player. These tests do not pretend to be real wallet approval tests.

Run `unity test . --mode EditMode --output Logs/editmode.xml`. C# tests cover out-of-order routing, duplicate/late results, cancellation/error codes, receiver loss, identity validation, and JSON account decoding. Compile and build the Android target separately because the Android editor hook and JNI call sites are conditionally compiled.

Run `unity test . --mode PlayMode --output Logs/playmode.xml` to validate the loaded demo's panel and wallet controls, and the Editor's explicit unsupported-platform behavior. The committed `docs/images/editor-preview.png` was rendered from the real Play-mode UI, not an illustrative mockup.

After building, run `scripts/verify-apk.ps1` to verify the APK signature, inspect ARM64/IL2CPP packaging and target/minimum SDK levels, check bridge/AndroidX/official SDK DEX class presence, and print the SHA-256 digest. This is static package verification, not an Android runtime JNI test.

## Real Seeker checklist (required before production use)

For the current demo on an ordinary Android phone: open Mock MWA Wallet, tap **Authenticate**, return to the demo, then tap **Connect with MWA test wallet**. The demo opts into Devnet-only MWA testing. For the certified Seeker checklist below, initialize the SDK with its default `allowMwaTesting = false`; the generic test mode cannot validate the certified-wallet gate.

1. Install a development APK on a Seeker with its wallet configured. Use a genuine app identity and accessible icon. Do not share seed phrases or private keys with the app.
2. Connect: approve in the wallet; compare the returned Base58 address with the selected wallet account. An initial decline must show an error and no successful session.
3. Sign the demo message. Independently verify the displayed 64-byte Ed25519 signature against UTF-8 `Hello from Unity Seeker Connect!` and the displayed public key.
4. Close and relaunch the app, configure the same identity, and Restore Session. A cached account should appear without a new wallet authorization screen. Sign again to verify reauthorization.
5. Build the Devnet memo and sign promptly. Decode the signed transaction and verify its signature and unchanged message. It must contain only the memo instruction and must not have been broadcast by this demo.
6. Reject message signing, press Android Back in the wallet, cancel from the host, and leave approval pending beyond the timeout. Each task should settle once, with an actionable error. Association cancellation before a session starts can be reported as `ASSOCIATION_FAILED` by the upstream SDK.
7. Background/foreground both apps; rotate; use Android's activity-destruction developer option. A destroyed/recreated host must not resubmit a request. Force-stop/relaunch must restore only cached authorization, never replay an in-flight transaction.
8. Disconnect, relaunch, and Restore Session: no authorization should remain for that identity/chain.
9. On a non-Seeker phone or without a certified verified endpoint, Connect must fail with `UNSUPPORTED_DEVICE`, even if some generic MWA wallet is installed.
10. If separately testing `SignAndSendTransactionAsync`, use Devnet and a funded Devnet account, request explicit wallet approval, and confirm the returned signature using RPC. On ambiguous failure, reconcile the transaction first. Do not automatically repeat it.

This project cannot validate any of the above without an actual device and wallet. Local unit tests and APK inspection cover only their stated scope.

## Common failures

| Symptom | Check |
| --- | --- |
| `UNSUPPORTED_PLATFORM` in Editor | Expected; Play mode is a UI preview, not a wallet emulator |
| `UNSUPPORTED_DEVICE` on Seeker | Wallet installed/configured; verified handling of `https://connect.solanamobile.com`; Android App Links settings |
| `NO_WALLET_FOUND` / `ASSOCIATION_FAILED` | Wallet visibility, App Links, wallet foreground behavior, user cancellation, association deadline |
| Class not found / `BRIDGE_UNAVAILABLE` | Entire UPM package installed, including `Native~`; Android build hook ran; inspect generated Gradle module and DEX |
| Kotlin metadata/AGP errors | Use recorded versions; do not silently downgrade transitive dependencies |
| SDK is read-only warning | Bundled SDK under Program Files may warn during package metadata scanning; if it becomes fatal, use a writable external SDK configured in Unity |
| Stale authorization declined | SDK clears rejected authorization; explicitly reconnect |
| Memo RPC fails | Public Devnet RPC may rate-limit or be offline; retry fetching a blockhash, not a transaction submission |
| Transaction rejected | Full serialization required; fresh blockhash; wallet support for transaction version; correct signer |
| Signing takes too long | Native timeout is configurable up to 120 seconds; Unity processes callbacks on resume |
| APK install fails | ARM64 device/API 26+; conflicting app signing certificate; sufficient storage |

The build hook intentionally prevents preference backups. Integrators with existing backup policies must reconcile those policies while continuing to exclude authorization tokens.
