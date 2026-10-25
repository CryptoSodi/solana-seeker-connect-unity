import { chromium } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
import assert from 'node:assert/strict';
await mkdir('../Logs', { recursive: true });
const browser = await chromium.launch({ channel: 'chrome', args: ['--enable-unsafe-swiftshader'] });
const page = await browser.newPage({ viewport: { width: 540, height: 1000 }, isMobile: true, userAgent: 'Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/141.0.0.0 Mobile Safari/537.36' });
page.on('pageerror', e => console.log('PAGE_ERROR:', e.message));
page.on('console', m => { if (m.type() === 'error') console.log('CONSOLE_ERROR:', m.text().slice(0, 500)); });
try {
  await page.goto('http://127.0.0.1:8090');
  await page.waitForFunction(() => document.querySelector('#unity-loading-container')?.style.display === 'none', null, { timeout: 120000 });
  await page.waitForTimeout(2000);
  await page.evaluate(() => {
    globalThis.bridgeReplies = [];
    const request = globalThis.UnitySeekerConnect.request.bind(globalThis.UnitySeekerConnect);
    globalThis.UnitySeekerConnect.request = (id, op, json, callback) => request(id, op, json, reply => {
      globalThis.bridgeReplies.push({ operation: op, reply: JSON.parse(reply) }); callback(reply);
    });
  });
  await page.mouse.move(450, 600); await page.mouse.wheel(0, 2500); await page.waitForTimeout(500);
  await page.mouse.click(260, 960);
  await page.waitForFunction(() => globalThis.bridgeReplies.some(r => r.operation === 'restore'), null, { timeout: 10000 });
  const replies = await page.evaluate(() => globalThis.bridgeReplies);
  assert.deepEqual(replies.map(r => r.operation), ['initialize', 'availability', 'restore']);
  assert.ok(replies.every(r => r.reply.ok));
  assert.equal(replies[1].reply.availability, 'UNKNOWN');
  assert.equal(replies[2].reply.session, null);
  console.log('PASS: real Unity C# → official web bridge → Unity callbacks: initialize, availability, empty restore.');
  await page.screenshot({ path: '../Logs/web-browser.png' });
  console.log('Unity loaded; screenshot saved. Official bridge:', await page.evaluate(() => typeof globalThis.UnitySeekerConnect?.request));
} finally { await browser.close(); }
