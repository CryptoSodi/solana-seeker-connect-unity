# Android production MWA wallets

Package 0.2.1 adds `allowGenericMwa`, an opt-in to the official Android Seeker Connect SDK's generic MWA fallback. Existing callers retain certified-only behavior unless they already enable mock testing.

```csharp
await SeekerConnectManager.InitializeAsync(new SeekerConnectConfig {
    appName = "My Game",
    identityUri = yourHttpsIdentity,
    iconPath = "images/icon.png",
    chain = "solana:mainnet", // Use the chain your application and wallet support.
    allowGenericMwa = true,
    allowMwaTesting = false
});
var restored = await SeekerConnectManager.RestoreSessionAsync();
// If restored is null, show a Connect button that calls ConnectAsync().
```

## Policy and validation

| allowGenericMwa | allowMwaTesting | Accepted wallets | Chain | Payload rules / cache |
| --- | --- | --- | --- | --- |
| false | false | Certified | Any supported chain | Strict / existing certified-only cache |
| true | false | Certified or compatible MWA | Any supported chain | Strict / separate production MWA cache |
| either | true | Certified or compatible MWA | Devnet only | Verified signatures / existing testing cache |

The production flag only changes availability policy and cache scope. Since 0.2.2, `Wire.signature` accepts the original message followed by a 64-byte signature or a detached 64-byte response, and verifies Ed25519 against the exact requested message and account key in every Android mode. Full payloads must preserve the exact message prefix. Transaction structural checks remain unchanged. Both flags true still reject Mainnet/Testnet in C# and Kotlin. The flags do not change WebGL transport or enable unsupported platforms.

The official SDK resolves a previously learned wallet URI first, then the default certified first-connect URI, then generic MWA. On first connection, certified routing remains preferred. A generic chooser may appear when multiple wallets are installed. The wrapper does not change upstream wallet selection.

## Jupiter selection

The pinned official Android SDK 0.2.0 has no public package-name selection setting. Its `firstConnectWalletBaseUri` supports a verified wallet URI, but overriding it would change the certified first-connect behavior; this wrapper does not expose it. No Jupiter URI is guessed or hard-coded.

On an ordinary Android phone, install and configure a compatible Jupiter Wallet version, enable `allowGenericMwa`, and select Jupiter if Android shows a chooser. Subsequent sessions can use the wallet URI learned through authorization. This is protocol support, not a claim that Jupiter has passed physical-device validation. If a certified wallet is installed, the default first connection prefers that wallet.

Upstream routing and configuration sources:
- [MwaSeekerLink](https://github.com/solana-mobile/seeker-connect/blob/main/android/seeker-connect/src/main/kotlin/com/solanamobile/seekerconnect/android/MwaSeekerLink.kt)
- [SeekerConnectConfig](https://github.com/solana-mobile/seeker-connect/blob/main/android/seeker-connect-core/src/main/kotlin/com/solanamobile/seekerconnect/SeekerConnectConfig.kt)

## Restoration and integration

Call initialization once, then restore on launch using the same identity, chain and policy. Cached authorization survives process death. Restore does not ask the wallet to approve or prove that its grant is still valid; signing opens a new interaction and reauthorizes. Handle revoked grants by offering an explicit reconnect. Do not automatically retry signing/submission.

Each policy has a separate cache. Switching from certified-only or mock testing to production generic MWA requires connecting once in the new mode. Existing caches are preserved and not migrated. Disconnect clears only the active cache and does not revoke the wallet-side grant.

For an intentional wallet-app switch, call Disconnect before Connect so the old token and learned endpoint are not reused. Since 0.2.2, malformed/invalid message signatures (`INVALID_RESPONSE`) and account changes during reauthorization (`ACCOUNT_CHANGED`) clear local authorization automatically and stop the operation. The next explicit Connect starts without that cached token or endpoint; fetch a fresh server challenge afterwards. The bridge does not retry signing. The official SDK also clears cached authorization when token reauthorization returns `AUTHORIZATION_DECLINED`, but ordinary request rejection and transport failure do not establish that a token is invalid.

Preserve server challenge verification for login: a cached address is not backend authentication. Preserve server transaction validation and confirmation for checkout. Adding this Android provider does not require replacing Phantom WebGL or a separate PSG1/Jupiter provider.

## Required device follow-up

On the intended Jupiter phone, check availability, connect, exact server-challenge signing with independent signature verification, transaction signing with unchanged message verification, force-stop/relaunch/restore followed by signing, rejection, revoked authorization, and disconnect/empty restore. A restored address alone does not validate live wallet compatibility. No APK or WebGL player was built or deployed for the 0.2.1 policy change.
