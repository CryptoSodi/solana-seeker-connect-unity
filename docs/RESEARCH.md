# Official source research

Checked on 2026-10-25 (Asia/Karachi).

- [Seeker Connect overview](https://docs.solanamobile.com/solana-mobile-stack/seeker-connect)
- [Official Kotlin quickstart](https://docs.solanamobile.com/solana-mobile-stack/seeker-connect-quickstart)
- [Official source](https://github.com/solana-mobile/seeker-connect), inspected commit `8e55896ed04e693a49ab2b9182966b8f903a6048`
- [Maven Central artifact](https://central.sonatype.com/artifact/com.solanamobile/seeker-connect)
- [Maven metadata](https://repo.maven.apache.org/maven2/com/solanamobile/seeker-connect/maven-metadata.xml): latest/release `0.2.0`
- Published `seeker-connect-0.2.0-sources.jar` inspected in addition to repository main, avoiding assumptions about unreleased source.
- [Solana.Unity-SDK](https://github.com/magicblock-labs/Solana.Unity-SDK): inspected `IWalletBase` and native mobile-stack layout. It is not required for this wrapper. Its broader wallet/RPC/account APIs do not replace the requested Kotlin Seeker SDK integration.

Verified public Kotlin calls used:

```kotlin
SeekerConnect(config, MwaSeekerLink(context), SharedPreferencesAuthorizationCache(context, cacheName))
sdk.walletAvailability()
sdk.connect(componentActivity)
sdk.restore()
sdk.signMessages(componentActivity, listOf(address), listOf(messageBytes))
sdk.signTransactions(componentActivity, listOf(transactionBytes))
sdk.signAndSendTransactions(componentActivity, listOf(transactionBytes), options)
sdk.disconnect()
sdk.authorization.value
```

The official source defines `WalletAvailability.CERTIFIED`, `OTHER`, and `NONE`. Its transport can fall back to generic MWA. This wrapper defaults to `CERTIFIED` only. The demo now explicitly opts into Devnet-only MWA testing, allowing `OTHER` without calling it certified or assuming PSG1 compatibility.

`connect` first restores authorization; `restore` reads cache without wallet interaction. Signing establishes a new transport session and reauthorizes. `disconnect` clears cache locally, not wallet-side revocation. Message signatures are appended to message bytes in the returned MWA signed payload. These semantics are reflected in the wrapper and documentation.

Rechecked for wrapper 0.2.2 on 2026-10-27: the published Android `MwaSeekerWallet.signMessages` uses `client.signMessages(...).signedPayloads`, not the detached-result helper. The MWA client's `SignMessagesFuture` (used by `signMessagesDetached`) documents a workaround for wallets returning signature-only payloads. Wrapper 0.2.2 implements compatible normalization with mandatory cryptographic verification, rather than interpreting detached bytes as inherently valid or attributing every such response to a mock wallet. Signing uses `sdk.transact` to check the account after reauthorization. Invalid signatures/account changes clear local authorization without retry. The official SDK itself clears cached tokens on declined reauthorization, but not ordinary signing rejection; deliberate wallet switching requires Disconnect before Connect.

The installed Unity Android source at `Editor/Data/PlaybackEngines/AndroidPlayer/Source/com/unity3d/player/UnityPlayerActivity.java` declares `extends Activity`, confirming it cannot be cast to AndroidX `ComponentActivity`. The proxy host is necessary and is registered separately, non-exported. Native host lifecycle behavior remains subject to real-device verification.

The installed Unity base Gradle template declares AGP 9.0.0; its Gradle distribution is 9.1.0 and Java is 17.0.18. The standalone native build uses the same AGP/Gradle generation. Kotlin/coroutine/lifecycle versions were verified from official Maven artifacts/source catalogs and by resolving/compiling them, rather than inferred from the desired C# API.
