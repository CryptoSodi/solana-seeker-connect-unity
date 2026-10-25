import test from 'node:test';
import assert from 'node:assert/strict';
import { verify, createPublicKey } from 'node:crypto';
import { createNostrSeekerLink } from '@solana-mobile/seeker-connect-web';
import { createBridge } from '../bridge.js';
import { FakeNostrWallet } from './fixtures/fakeNostrWallet.ts';
import { TEST_PUBLIC_KEY } from './fixtures/fakeWalletCore.ts';
globalThis.CloseEvent ??= class extends Event { constructor(type, init = {}) { super(type); Object.assign(this, init); } };

const config = { appName: 'Unity test', identityUri: 'https://example.com', iconPath: 'icon.png', chain: 'solana:devnet', timeoutMs: 10000, relayDomain: 'relay.example.com' };
const storage = () => { const m = new Map(); return { getItem: k => m.get(k) ?? null, setItem: (k,v) => m.set(k,v), removeItem: k => m.delete(k) }; };
let seq = 0;
const call = (b, op, data = {}, id = String(++seq)) => new Promise(resolve => b.request(id, op, JSON.stringify(data), s => resolve(JSON.parse(s))));
const payload = bytes => ({ payload: Buffer.from(bytes).toString('base64') });
const publicKey = createPublicKey({ format: 'der', type: 'spki', key: Buffer.concat([Buffer.from('302a300506032b6570032100', 'hex'), TEST_PUBLIC_KEY]) });

test('official SDK: encrypted connect, real signatures, restore, send mapping and disconnect', async () => {
  const wallet = new FakeNostrWallet(); wallet.install();
  try {
    const store = storage(), bridge = createBridge(createNostrSeekerLink(), store);
    assert.equal((await call(bridge, 'initialize', config)).ok, true);
    assert.equal((await call(bridge, 'availability')).availability, 'UNKNOWN');
    const connected = await call(bridge, 'connect'); assert.equal(connected.ok, true, JSON.stringify(connected));
    assert.equal(JSON.stringify(connected).includes('FAKE_AUTH_TOKEN'), false);
    const message = Buffer.from('Hello from Unity Seeker Connect!');
    const signed = await call(bridge, 'signMessage', payload(message)); assert.equal(signed.ok, true, JSON.stringify(signed));
    assert.equal(verify(null, message, publicKey, Buffer.from(signed.signature, 'base64')), true);
    const tx = new Uint8Array(150); tx[0] = 1; tx[65] = 1;
    const result = await call(bridge, 'signTransaction', payload(tx)); assert.equal(result.ok, true, JSON.stringify(result));
    const signedTx = Buffer.from(result.payload, 'base64');
    assert.equal(verify(null, tx.slice(65), publicKey, signedTx.subarray(1,65)), true);
    assert.deepEqual(signedTx.subarray(65), Buffer.from(tx.slice(65)));
    // Fixture only: no RPC broadcast occurs.
    assert.equal((await call(bridge, 'signAndSend', payload(tx))).ok, true);
    const reloaded = createBridge(createNostrSeekerLink(), store);
    await call(reloaded, 'initialize', config);
    assert.equal((await call(reloaded, 'restore')).session.accounts[0].address, connected.session.accounts[0].address);
    assert.equal((await call(reloaded, 'signMessage', payload(message))).ok, true);
    assert.equal(wallet.authorizeRequests.at(-1).authToken, 'FAKE_AUTH_TOKEN');
    assert.equal((await call(reloaded, 'disconnect')).session, null);
    assert.equal((await call(reloaded, 'restore')).session, null);
    assert.equal((await call(reloaded, 'signMessage', payload(message))).code, 'NOT_CONNECTED');
    assert.deepEqual(wallet.errors, []);
    assert.equal(wallet.openSessions, 0);
    assert.equal(wallet.sessionsEstablished, 5);
  } finally { wallet.uninstall(); }
});

test('official SDK authorization rejection is surfaced without token leakage', async () => {
  const wallet = new FakeNostrWallet({ authorizeError: { code: -1, message: 'secret should not be logged' } }); wallet.install();
  try {
    const b = createBridge(createNostrSeekerLink(), storage()); await call(b, 'initialize', config);
    const r = await call(b, 'connect'); assert.equal(r.code, 'AUTHORIZATION_DECLINED'); assert.equal(JSON.stringify(r).includes('secret'), false);
    assert.equal((await call(b, 'restore')).session, null);
  } finally { wallet.uninstall(); }
});

test('cancelled late authorization cannot overwrite storage; concurrent calls are rejected', async () => {
  let resume;
  const link = { transact: async (c, callback) => { await new Promise(r => { resume = r; }); return callback({}); } };
  const b = createBridge(link, storage()); await call(b, 'initialize', config);
  const pending = call(b, 'connect', {}, 'cancel-me');
  assert.equal((await call(b, 'connect')).code, 'BUSY');
  b.cancel('cancel-me'); assert.equal((await pending).code, 'CANCELLED'); resume();
  await new Promise(r => setTimeout(r, 10));
  assert.equal((await call(b, 'restore')).session, null);
});

test('missing relay is rejected and initialization can be retried', async () => {
  const b = createBridge({}, storage());
  assert.equal((await call(b, 'initialize', { ...config, relayDomain: '' })).code, 'INVALID_RELAY');
  assert.equal((await call(b, 'initialize', config)).ok, true);
});

test('unresponsive official SDK association times out and permits another request', async () => {
  const wallet = new FakeNostrWallet({ unresponsive: true }); wallet.install();
  try {
    const b = createBridge(createNostrSeekerLink(), storage()); await call(b, 'initialize', config);
    assert.equal((await call(b, 'connect')).code, 'TIMEOUT');
    assert.equal((await call(b, 'restore')).session, null);
  } finally { wallet.uninstall(); }
});

test('changed account and mutated transaction are rejected before accepting signatures', async () => {
  let address = 'original';
  const wallet = {
    authorize: async () => ({ authToken: 'test-token', accounts: [{ address, publicKey: TEST_PUBLIC_KEY }] }),
    getCapabilities: async () => ({ supportsSignAndSendTransactions: false }),
    signTransactions: async ({ payloads }) => { const tx = payloads[0].slice(); tx[100] ^= 1; return [tx]; }
  };
  const b = createBridge({ transact: (_, cb) => cb(wallet) }, storage()); await call(b, 'initialize', config); await call(b, 'connect');
  const tx = new Uint8Array(150); tx[0] = 1;
  assert.equal((await call(b, 'signTransaction', payload(tx))).code, 'INVALID_RESPONSE');
  assert.equal((await call(b, 'signAndSend', payload(tx))).code, 'UNSUPPORTED_OPERATION');
  address = 'changed';
  assert.equal((await call(b, 'signTransaction', payload(tx))).code, 'ACCOUNT_CHANGED');
  assert.equal((await call(b, 'restore')).session, null);
});
