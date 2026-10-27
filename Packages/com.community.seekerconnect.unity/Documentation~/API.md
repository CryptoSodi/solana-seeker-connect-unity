# C# API reference

Namespace: `Community.SeekerConnect`. Entry point: static `SeekerConnectManager`.

| Member | Result / behavior |
| --- | --- |
| `InitializeAsync(SeekerConnectConfig)` | Validates identity, initializes the native SDK once per process |
| `GetWalletAvailabilityAsync()` | `None`, `Other`, or `Certified`; no wallet launch |
| `ConnectAsync(CancellationToken = default)` | Cached or newly approved `WalletSession` |
| `RestoreSessionAsync(CancellationToken = default)` | Cached `WalletSession`, or null; no wallet approval |
| `Session` | Most recent sanitized session snapshot, or null |
| `State` | `Uninitialized`, `Disconnected`, `Connecting`, `Connected`, `Unsupported` |
| `ConnectionStateChanged` | `Action<WalletConnectionState>`, invoked on Unity's main thread |
| `SignMessageAsync(string, CancellationToken = default)` | `WalletSignature` containing detached 64-byte signature and full signed payload |
| `SignTransactionAsync(byte[], CancellationToken = default)` | Fully serialized signed transaction bytes |
| `SignAndSendTransactionAsync(byte[], CancellationToken = default)` | Submission `WalletSignature`; caller must confirm via RPC |
| `DisconnectAsync()` | Clears local official SDK cache, does not revoke wallet grant |

Call every entry point on Unity's main thread after runtime initialization. The facade deliberately throws on background-thread calls rather than making Unity APIs race. Standard `await` from Unity callbacks resumes in Unity's synchronization context. Do not use `ConfigureAwait(false)` before modifying Unity UI.

`SeekerConnectConfig` has `appName`, `identityUri` (absolute HTTPS), `iconPath` (relative URI), `chain` (`solana:devnet` by default), and `timeoutMs` (10000–120000). Do not mutate configuration expecting a running SDK to change. Initialize once.

Android wallet policy options both default to `false`:

- `allowGenericMwa = true`: production opt-in for compatible generic MWA wallets on Devnet, Testnet or Mainnet. Does not relax message or transaction validation. Certified first-connect routing remains preferred; the official SDK falls back to the Android MWA chooser.
- `allowMwaTesting = true`: Devnet-only mock testing policy with a separate cache. The chain restriction is validated on both sides of JNI, even when `allowGenericMwa` is also true. The demo enables this option. Since 0.2.2 it does not change signature verification rules.

Certified-only, production generic-MWA, and mock-testing authorizations have separate caches, additionally scoped by identity and chain. Existing certified-only and mock cache names remain unchanged. If both flags are true, mock mode and its cache apply. Changing policy requires a new connection in its cache; no tokens are migrated. These flags do not change WebGL transport.

Call `RestoreSessionAsync()` after initialization on app startup. It restores cached authorization without wallet approval; the next signing interaction reauthorizes with the wallet. Restore is not backend authentication, and revoked/expired wallet grants may fail on the next operation. Disconnect clears only the current configuration's local cache.

`WalletSession` contains `chain`, `accounts`, `Account` (first authorized account), and `supportsSignAndSend`. `WalletAccount` has `address`, `publicKeyBase64`, `PublicKey`, and optional `label`. Treat snapshot fields/arrays as read-only. The wrapper signs with the first authorized account.

`WalletSignature` exposes `Address`, `Signature`, `Base64`, and `SignedPayload`. `SignedPayload` is populated for messages, not sign-and-send. It does not represent network confirmation.

Since 0.2.2, Android accepts full or detached 64-byte message-signature responses in every mode, verifies Ed25519 against the requested message and account key, and normalizes `SignedPayload` to message followed by signature. Reauthorization must retain the same first account before signing. Invalid signatures return `INVALID_RESPONSE`; changed accounts return `ACCOUNT_CHANGED`. Both clear local authorization and return a null session; reconnect explicitly and obtain a fresh server challenge. No signing is automatically retried. Server-side verification remains necessary for login. To switch wallets intentionally, call Disconnect before Connect; Connect alone can reuse the cached authorization.

## Errors

Catch `SeekerConnectException` and inspect `Code`. Invalid managed inputs may throw `ArgumentException` before JNI. A cancellation token cancelled before dispatch raises `OperationCanceledException`; cancellation after dispatch is reported through the native `CANCELLED` code.

| Code | Meaning / action |
| --- | --- |
| `UNSUPPORTED_PLATFORM` | Editor/non-Android: use a real Android build |
| `UNSUPPORTED_DEVICE` | No certified Seeker endpoint and neither Android fallback opt-in is enabled |
| `NO_WALLET_FOUND` | Official SDK cannot resolve wallet; check installation/App Links |
| `NOT_INITIALIZED`, `NOT_CONNECTED` | Initialize, then connect/restore |
| `BUSY` | Await/cancel existing request before another operation |
| `INVALID_INPUT`, `INVALID_RESPONSE` | Check input, encoding, or unexpected wallet response shape |
| `ACCOUNT_CHANGED` | Android reauthorization changed the account; cache cleared, no message signed; reconnect and obtain a fresh challenge |
| `AUTHORIZATION_DECLINED`, `REQUEST_DECLINED` | Wallet/user declined; do not automatically retry |
| `ASSOCIATION_FAILED` | SDK association timeout/failure; check wallet App Links |
| `TIMEOUT`, `SESSION_CLOSED` | Outcome may be ambiguous for submission; reconcile with RPC |
| `CANCELLED`, `ACTIVITY_RECREATED` | Request stopped; explicit next action required |
| `UNSUPPORTED_OPERATION` | Wallet did not advertise sign-and-send support |
| `BRIDGE_UNAVAILABLE` | Native class/dependencies missing; rebuild with package hook |
| `WALLET_ERROR` | Other failure, no successful submission/confirmation implied |

Error messages are sanitized; native exception payloads and authorization tokens are never logged or returned to Unity. The demo displays public addresses and signatures only.
