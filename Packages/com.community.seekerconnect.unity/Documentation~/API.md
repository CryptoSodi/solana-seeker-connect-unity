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

`allowMwaTesting` defaults to `false`. Explicitly set it to `true` to allow generic MWA wallets such as Mock MWA Wallet on ordinary Android devices. This option requires Devnet and is validated on both sides of JNI. Testing-mode authorizations are isolated from the certified-only cache. The demo enables this option and labels its UI accordingly.

`WalletSession` contains `chain`, `accounts`, `Account` (first authorized account), and `supportsSignAndSend`. `WalletAccount` has `address`, `publicKeyBase64`, `PublicKey`, and optional `label`. Treat snapshot fields/arrays as read-only. The wrapper signs with the first authorized account.

`WalletSignature` exposes `Address`, `Signature`, `Base64`, and `SignedPayload`. `SignedPayload` is populated for messages, not sign-and-send. It does not represent network confirmation.

## Errors

Catch `SeekerConnectException` and inspect `Code`. Invalid managed inputs may throw `ArgumentException` before JNI. A cancellation token cancelled before dispatch raises `OperationCanceledException`; cancellation after dispatch is reported through the native `CANCELLED` code.

| Code | Meaning / action |
| --- | --- |
| `UNSUPPORTED_PLATFORM` | Editor/non-Android: use a real Android build |
| `UNSUPPORTED_DEVICE` | No certified Seeker endpoint; generic MWA fallback deliberately disabled |
| `NO_WALLET_FOUND` | Official SDK cannot resolve wallet; check installation/App Links |
| `NOT_INITIALIZED`, `NOT_CONNECTED` | Initialize, then connect/restore |
| `BUSY` | Await/cancel existing request before another operation |
| `INVALID_INPUT`, `INVALID_RESPONSE` | Check input, encoding, or unexpected wallet response shape |
| `AUTHORIZATION_DECLINED`, `REQUEST_DECLINED` | Wallet/user declined; do not automatically retry |
| `ASSOCIATION_FAILED` | SDK association timeout/failure; check wallet App Links |
| `TIMEOUT`, `SESSION_CLOSED` | Outcome may be ambiguous for submission; reconcile with RPC |
| `CANCELLED`, `ACTIVITY_RECREATED` | Request stopped; explicit next action required |
| `UNSUPPORTED_OPERATION` | Wallet did not advertise sign-and-send support |
| `BRIDGE_UNAVAILABLE` | Native class/dependencies missing; rebuild with package hook |
| `WALLET_ERROR` | Other failure, no successful submission/confirmation implied |

Error messages are sanitized; native exception payloads and authorization tokens are never logged or returned to Unity. The demo displays public addresses and signatures only.
