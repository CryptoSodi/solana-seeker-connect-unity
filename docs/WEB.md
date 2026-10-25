# Unity WebGL / official Seeker Connect web SDK

The same `SeekerConnectManager` C# API now calls the official
`@solana-mobile/seeker-connect-web@0.2.0` in WebGL. Android continues to call
the official Kotlin SDK. The JavaScript bridge adds Unity callbacks and local
authorization caching; it does not replace the official wallet transport.

## Build and run

Install Unity 6000.6.0f1 Web Build Support and Node.js 22 or later.

```powershell
cd web
npm ci
npm test
npm run build
cd ..
unity run . --timeout 1800 -- -buildTarget WebGL -executeMethod DemoBuilder.BuildWebGL
cd web
node serve.js
```

Open http://127.0.0.1:8090. Production hosting must use HTTPS and serve `.wasm`
as `application/wasm`. The package build hook copies the bundled official SDK
and loads it before the Unity loader; custom templates need a closing head tag.
The bundle is included in the package, so consuming Unity projects do not need Node.

The demo prefills Solana Mobile's free relay. Before making real connections,
accept the relay terms on the [official quickstart](https://docs.solanamobile.com/solana-mobile-stack/seeker-connect-quickstart#relay-url).
You do not need to host a server. Configure it in your own C# application:

```csharp
await SeekerConnectManager.InitializeAsync(new SeekerConnectConfig {
    appName = "My Game",
    identityUri = "https://your-real-app.example",
    iconPath = "icon.png",
    chain = "solana:devnet",
    relayDomain = "relay.solanamobile.com" // Accept relay terms first
});
await SeekerConnectManager.ConnectAsync();
```

The reusable configuration requires an explicit relay. The official SDK uses this
setting and opens its official wallet association URL. A compatible wallet and
reachable WSS relay are required for real approvals. Android Mock MWA testing
does not demonstrate compatibility with this Nostr transport. `allowMwaTesting`
does not switch the web build to Android's local MWA transport.

## Session and error behavior

- Restore reads cached authorization without opening the wallet. Signing always
  reauthorizes. Account changes fail and clear the cached session.
- Tokens stay in JavaScript localStorage, scoped to origin, identity, chain and
  relay. They are not sent to Unity or logged. All scripts on the same origin can
  access localStorage; host the app on a trusted origin and protect against XSS.
- Disconnect removes local authorization; it does not revoke the wallet grant.
- Browser wallet availability is `Unknown`, because installation cannot be detected.
- Cancellation and timeout complete once; late callbacks cannot restore a session.
- Sign-and-send returns a submission signature, not confirmation. Never retry an
  ambiguous timeout automatically. The demo does not broadcast transactions.

## Verification boundary

`npm test` runs the published official SDK with an adapted upstream wallet/relay
fixture. Nostr event signatures, association cryptography and encrypted JSON-RPC
run through the real client. A public test-only Ed25519 key signs messages and
transaction bytes; Node crypto independently verifies the signatures.
The suite covers restore after bridge recreation, disconnect, authorization
rejection, timeout, cancellation, concurrent requests, malformed/mutated replies,
account changes and capability gating. Sign-and-send tests only response mapping;
the fixture does not broadcast. This is not physical Seeker or public relay validation.

Upstream: https://github.com/solana-mobile/seeker-connect/tree/main/js/packages/web
