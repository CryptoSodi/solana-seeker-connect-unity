const encode = bytes => btoa(String.fromCharCode(...bytes));
const decode = text => Uint8Array.from(atob(text), c => c.charCodeAt(0));
const fail = code => Object.assign(new Error(code), { code });
const equal = (a, b) => a.length === b.length && a.every((v, i) => v === b[i]);

// The transport and wallet protocol are provided by the official SDK. This layer
// owns only Unity messages, cancellation and origin-local authorization storage.
export function createBridge(link, storage) {
  let config, key, session = null, active = null;
  function clear() { session = null; storage.removeItem(key); }
  function publicSession(value) {
    return value && { chain: config.chain, accounts: value.accounts, supportsSignAndSend: value.supportsSignAndSend };
  }
  function validate(c) {
    const identity = new URL(c.identityUri);
    if (identity.protocol !== 'https:' || identity.username || identity.password || identity.hash || !c.appName)
      throw fail('INVALID_CONFIG');
    if (!c.relayDomain || !/^[a-zA-Z0-9.-]+(?::\d{1,5})?$/.test(c.relayDomain)) throw fail('INVALID_RELAY');
    if (!['solana:devnet', 'solana:testnet', 'solana:mainnet'].includes(c.chain) || c.timeoutMs < 10000 || c.timeoutMs > 120000)
      throw fail('INVALID_CONFIG');
  }
  function read() {
    try {
      const value = JSON.parse(storage.getItem(key));
      if (!value) return null;
      if (typeof value.authToken !== 'string' || !value.authToken || !value.accounts?.length ||
          value.accounts.some(a => !a.address || decode(a.publicKeyBase64).length !== 32)) throw fail('INVALID_CACHE');
      if (value.walletUriBase && new URL(value.walletUriBase).protocol !== 'https:') throw fail('INVALID_CACHE');
      return value;
    } catch { clear(); return null; }
  }
  return {
    cancel(id) { if (active?.id === id) active.cancel('CANCELLED'); },
    async request(id, operation, json, deliver) {
      if (active) { deliver(JSON.stringify({ id, ok: false, code: 'BUSY', message: 'A wallet operation is already running.' })); return; }
      const controller = new AbortController();
      let done = false, timer;
      const finish = result => {
        if (done) return;
        done = true; clearTimeout(timer); active = null;
        deliver(JSON.stringify({ id, ...result }));
      };
      const check = () => { if (done) throw fail('CANCELLED'); };
      active = { id, cancel(code) { controller.abort(); finish({ ok: false, code, message: code === 'TIMEOUT' ? 'Wallet request timed out. Check transaction status before retrying.' : 'Wallet request cancelled.' }); } };
      try {
        const input = JSON.parse(json);
        if (operation === 'initialize') {
          if (config) throw fail('ALREADY_INITIALIZED');
          validate(input); config = input;
          key = 'unity-seeker-v1:' + JSON.stringify([config.identityUri, config.chain, config.relayDomain]);
          finish({ ok: true }); return;
        }
        if (!config) throw fail('NOT_INITIALIZED');
        if (operation === 'availability') { finish({ ok: true, availability: 'UNKNOWN' }); return; }
        if (operation === 'disconnect') { clear(); finish({ ok: true, hasSession: true, session: null }); return; }
        if (operation === 'restore') { session = read(); finish({ ok: true, hasSession: true, session: publicSession(session) }); return; }
        if (!['connect', 'signMessage', 'signTransaction', 'signAndSend'].includes(operation)) throw fail('UNSUPPORTED_OPERATION');
        if (operation !== 'connect' && !session) throw fail('NOT_CONNECTED');
        timer = setTimeout(() => active?.id === id && active.cancel('TIMEOUT'), config.timeoutMs);
        const previous = session || read();
        const output = await link.transact({
          identity: { name: config.appName, uri: config.identityUri, icon: config.iconPath },
          chain: config.chain, relayDomain: config.relayDomain, associationTimeoutMs: config.timeoutMs
        }, async wallet => {
          check();
          const authorization = await wallet.authorize({ chain: config.chain, ...(previous ? { authToken: previous.authToken } : {}) });
          check();
          if (!authorization.accounts?.length || !authorization.authToken) throw fail('INVALID_RESPONSE');
          const accounts = authorization.accounts.map(a => {
            if (a.publicKey?.length !== 32 || !a.address) throw fail('INVALID_RESPONSE');
            return { address: a.address, publicKeyBase64: encode(a.publicKey), label: a.label || '' };
          });
          // Never sign a payload prepared for a different account after reauthorization.
          if (operation !== 'connect' && accounts[0].address !== session.accounts[0].address) throw fail('ACCOUNT_CHANGED');
          const capabilities = await wallet.getCapabilities(); check();
          const next = { authToken: authorization.authToken, walletUriBase: authorization.walletUriBase,
            accounts, supportsSignAndSend: !!capabilities.supportsSignAndSendTransactions };
          let result = {};
          if (operation !== 'connect') {
            const payload = decode(input.payload);
            if (operation === 'signMessage') {
              if (!payload.length || payload.length > 32768) throw fail('INVALID_PAYLOAD');
              const signed = (await wallet.signMessages({ addresses: [accounts[0].address], payloads: [payload] }))[0]; check();
              if (!signed || signed.length !== payload.length + 64 || !equal(signed.slice(0, payload.length), payload)) throw fail('INVALID_RESPONSE');
              result = { signature: encode(signed.slice(-64)), signedPayload: encode(signed), address: accounts[0].address };
            } else {
              if (payload.length < 100 || payload.length > 1232) throw fail('INVALID_PAYLOAD');
              if (operation === 'signTransaction') {
                const signed = (await wallet.signTransactions({ payloads: [payload] }))[0]; check();
                // Compact-u16 signature count, followed by signatures and the immutable message.
                let count = 0, shift = 0, offset = 0;
                do { if (offset >= 3) throw fail('INVALID_PAYLOAD'); const n = payload[offset++]; count |= (n & 127) << shift; shift += 7; if (!(n & 128)) break; } while (true);
                const start = offset + count * 64;
                if (!count || start >= payload.length || !signed || signed.length !== payload.length ||
                    !equal(signed.slice(0, offset), payload.slice(0, offset)) || !equal(signed.slice(start), payload.slice(start))) throw fail('INVALID_RESPONSE');
                result = { payload: encode(signed) };
              } else {
                if (!next.supportsSignAndSend) throw fail('UNSUPPORTED_OPERATION');
                const sig = (await wallet.signAndSendTransactions({ payloads: [payload] }))[0]; check();
                if (sig?.length !== 64) throw fail('INVALID_RESPONSE');
                result = { signature: encode(sig), address: accounts[0].address };
              }
            }
          }
          check(); storage.setItem(key, JSON.stringify(next)); session = next;
          return result;
        }, { signal: controller.signal, ...(previous?.walletUriBase ? { walletUriBase: previous.walletUriBase } : {}) });
        check(); finish({ ok: true, hasSession: true, session: publicSession(session), ...output });
      } catch (error) {
        if (done) return;
        const code = typeof error?.code === 'string' ? error.code.toUpperCase().replaceAll('-', '_') : 'WALLET_ERROR';
        if (['AUTHORIZATION_DECLINED', 'ACCOUNT_CHANGED'].includes(code)) clear();
        // Never forward SDK exception text: it may include authorization material.
        finish({ ok: false, code, message: 'Seeker Connect: ' + code, hasSession: true, session: publicSession(session) });
      }
    }
  };
}
