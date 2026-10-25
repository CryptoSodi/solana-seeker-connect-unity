// Verify public development-build artifacts independently with Node's Ed25519 implementation.
// Usage: node scripts/verify-device-evidence.cjs <directory containing *-evidence.json>
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
function base58(value) {
  const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
  let n = 0n;
  for (const c of value) { const digit = alphabet.indexOf(c); assert(digit >= 0); n = n * 58n + BigInt(digit); }
  let hex = n.toString(16); if (hex.length % 2) hex = '0' + hex;
  return Buffer.concat([Buffer.alloc(value.match(/^1*/)[0].length), n === 0n ? Buffer.alloc(0) : Buffer.from(hex, 'hex')]);
}
function verify(address, message, signature) {
  const raw = base58(address); assert.equal(raw.length, 32); assert.equal(signature.length, 64);
  const key = crypto.createPublicKey({ key: Buffer.concat([Buffer.from('302a300506032b6570032100', 'hex'), raw]), format: 'der', type: 'spki' });
  assert(crypto.verify(null, message, key, signature), 'Invalid Ed25519 signature');
}
const directory = process.argv[2] || 'Logs';
const message = JSON.parse(fs.readFileSync(path.join(directory, 'message-evidence.json'), 'utf8'));
assert.equal(message.input, 'Hello from Unity Seeker Connect!');
verify(message.address, Buffer.from(message.input, 'utf8'), Buffer.from(message.output, 'base64'));
console.log('PASS: message signature independently verified');
const transaction = JSON.parse(fs.readFileSync(path.join(directory, 'transaction-evidence.json'), 'utf8'));
const unsigned = Buffer.from(transaction.input, 'base64');
const signed = Buffer.from(transaction.output, 'base64');
assert.equal(unsigned[0], 1); assert.equal(signed[0], 1);
assert(unsigned.subarray(1, 65).equals(Buffer.alloc(64)));
assert(signed.subarray(65).equals(unsigned.subarray(65)), 'Wallet changed transaction message');
assert(signed.subarray(69, 101).equals(base58(transaction.address)), 'Unexpected fee payer');
assert(signed.subarray(65, 69).equals(Buffer.from([1, 0, 1, 2])), 'Unexpected message header');
assert(signed.subarray(101, 133).equals(base58('MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr')), 'Unexpected program');
const memo = Buffer.from('Hello from Unity Seeker Connect!');
assert(signed.subarray(165).equals(Buffer.concat([Buffer.from([1, 1, 1, 0, memo.length]), memo])), 'Unexpected instruction');
verify(transaction.address, signed.subarray(65), signed.subarray(1, 65));
console.log('PASS: transaction signature independently verified; message unchanged; memo-only instruction');
