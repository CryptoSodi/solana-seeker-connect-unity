These two TypeScript fixtures are adapted from Solana Mobile seeker-connect,
commit 8e55896ed04e693a49ab2b9182966b8f903a6048, js/packages/web/test/.
Copyright Solana Mobile; Apache-2.0 (LICENSE alongside).

Local modifications: wallet signing uses a deterministic, public TEST-ONLY
Ed25519 key; transaction signatures occupy the serialized signature slot.
The relay/browser are simulated. The official client SDK, Nostr event signatures,
association handshake, encryption, and JSON-RPC serialization run unchanged.
This does not test a public relay or a physical Seeker wallet. Never fund this key.
