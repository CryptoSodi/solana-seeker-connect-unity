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

The official source defines `WalletAvailability.CERTIFIED`, `OTHER`, and `NONE`. Its transport can fall back to generic MWA. This wrapper explicitly gates wallet operations on `CERTIFIED`, satisfying the Seeker-specific demo requirement without pretending PSG1 or generic wallets are certified.

`connect` first restores authorization; `restore` reads cache without wallet interaction. Signing establishes a new transport session and reauthorizes. `disconnect` clears cache locally, not wallet-side revocation. Message signatures are appended to message bytes in the returned MWA signed payload. These semantics are reflected in the wrapper and documentation.

The installed Unity Android source at `Editor/Data/PlaybackEngines/AndroidPlayer/Source/com/unity3d/player/UnityPlayerActivity.java` declares `extends Activity`, confirming it cannot be cast to AndroidX `ComponentActivity`. The proxy host is necessary and is registered separately, non-exported. Native host lifecycle behavior remains subject to real-device verification.

The installed Unity base Gradle template declares AGP 9.0.0; its Gradle distribution is 9.1.0 and Java is 17.0.18. The standalone native build uses the same AGP/Gradle generation. Kotlin/coroutine/lifecycle versions were verified from official Maven artifacts/source catalogs and by resolving/compiling them, rather than inferred from the desired C# API.
