# Architecture and reliability

## WebGL transport

WebGL uses the same C# facade and request router, with `.jslib` externs calling
the bundled official `@solana-mobile/seeker-connect-web@0.2.0`. Each interaction
runs through `createNostrSeekerLink().transact`. The bridge adds origin-local
authorization storage, public account snapshots, cancellation and a JavaScript
deadline (rather than relying on threaded `Task.Delay` in WebGL). It checks signed
message prefixes and that signed transactions retain the original message bytes;
it does not duplicate Android's full transaction parser. Wallet approval remains
required. Browser availability is `Unknown`. See [web setup](WEB.md) for relay
configuration, storage limitations, and the automated test boundary.

The following native lifecycle and SharedPreferences details apply to Android.

## Request lifecycle

The static C# facade captures Unity's main thread at runtime initialization. A persistent GameObject receives `UnitySendMessage`. Each call registers a GUID in `RequestRouter`, invokes the native static Java API, and awaits a `TaskCompletionSource` with asynchronous continuations. Unity callbacks update state on the Unity main thread before completing the task. Unknown, duplicate, or late IDs cannot settle another request. Subscriber exceptions cannot prevent request completion.

Android receives JSON on the main looper, checks its independent busy gate, then launches a non-exported `WalletHostActivity`. Its `lifecycleScope` calls the official SDK. The SDK opens/closes each MWA transport and registers its activity result launcher against this `ComponentActivity`. UnityPlayerActivity remains unchanged underneath it.

Returning from the wallet resumes Unity normally. Pausing/stopping the host while the wallet is foregrounded does not cancel its coroutine. Destruction does cancel it. Orientation, screen-size, keyboard, and UI-mode changes are handled in place; recreation through any other cause fails the outstanding operation with `ACTIVITY_RECREATED`, without repeating a signing or submission request. If Android kills the entire process, there is no surviving pending Task: initialize and restore on next launch. An orphan host with no in-memory request simply closes.

Native operations have a configured 10–120 second timeout, plus a deadline covering activity launch and SDK cleanup. The C# watchdog is longer to allow SDK cleanup and callback delivery. Cancellation tokens request native cancellation; the bridge then reports a `CANCELLED` exception. A C# Task cannot complete while Unity itself is suspended, so callbacks may be observed only after resume. Never treat timeout/cancellation of sign-and-send as proof nothing was submitted.

Only one operation runs at a time on each side. Conflicting operations, including Disconnect while signing, return `BUSY`. Cancel the pending operation and await its completion, then disconnect. There are no automatic signing/submission retries.

## Sessions

`SharedPreferencesAuthorizationCache` is the official implementation. It stores the SDK-issued authorization in app-private preferences, namespaced by identity URI and chain; the Unity bridge never serializes its token. A restored session is cached authorization, not a persistent socket and not proof the wallet still honors it. Signing opens a new session and reauthorizes; a declined stale token is cleared by the SDK. Native replies include the current sanitized account snapshot even on failure, so Unity reflects this invalidation.

Disconnect invokes the official `disconnect()`, clearing local authorization without launching the wallet. It does **not** call wallet-side deauthorization. Users can revoke the app grant in their wallet. No private keys or seed phrases are requested, generated, or stored.

Backups are disabled and preferences excluded from device transfer by the build hook. The official cache is app-private, not an additional encrypted-keystore cache; this distinction matters on compromised/rooted devices. Configuration changes do not accidentally authorize a different identity/chain because the cache name changes.

## Wire data

JSON bridges only account public data, errors, request IDs and Base64 byte arrays. Addresses are Base58 as returned by the SDK. Public keys are also exposed as raw 32-byte values via `WalletAccount.PublicKey`.

The published Android SDK 0.2.0 `signMessages` calls the raw MWA `signMessages` API and returns signed payloads, normally **original message followed by a 64-byte signature**. It does not call `signMessagesDetached` in this path. Some wallets return only the signature; the MWA client's detached-result helper explicitly accommodates that encoding. Since wrapper 0.2.2, the Android bridge accepts either encoding and verifies Ed25519 against the exact original message and account key using Bouncy Castle's lightweight API. Full payload prefixes and lengths are checked as well. `SignedPayload` is normalized to message plus signature. The check applies equally to Mainnet and mock mode. C# encodes strings as strict UTF-8 and limits input to 32768 bytes.

Message signing now uses the official SDK's `transact` to check the freshly reauthorized account before requesting a signature. Account changes stop with `ACCOUNT_CHANGED`; malformed or unverifiable results stop with `INVALID_RESPONSE`. Either clears local authorization so an explicit next Connect cannot reuse the failed token or endpoint. No automatic retry occurs. Backend challenge verification remains required; this Android change does not alter the WebGL bridge or transaction signing.

Transactions are full serialized transactions, including signature slots, not message-only bytes. The native parser bounds size to 1232 bytes and walks legacy/v0 headers, account keys, blockhash, instructions, and address-table lookups. It rejects truncation, trailing data, invalid shortvecs and signature/header mismatches. It is structural validation, not a transaction-policy or account-ownership validator. The wallet remains responsible for signing approval. The demo constructs only a Devnet memo, with the connected account as fee payer/signer and a fresh RPC blockhash; it never sends it.

Sign-and-send returns the wallet's 64-byte submission signature only. RPC confirmation and blockhash-expiry reconciliation are the calling application's responsibility. No confirmed-success claim is emitted.

## Extensibility

The demo opts into `allowMwaTesting`, using the official SDK's generic MWA fallback on Devnet. Missing wallets are never accepted. The SDK configuration remains certified-only by default; its production cache is separate from the testing cache. This mode exercises real Android wallet communication but does not certify a Seeker device flow.

The C# API accepts standard serialized Solana bytes. Kotlin and JavaScript bridges
select the official platform SDKs at compile time. A future `IWalletProvider`
adapter could expose the same account/signing models for other providers. PSG1
support requires separate research and testing; this implementation makes no claim.
