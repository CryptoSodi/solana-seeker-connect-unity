import { build } from 'esbuild';
import { readFile, writeFile, readdir } from 'node:fs/promises';
import path from 'node:path';
const output = '../Packages/com.community.seekerconnect.unity/Web~/';
const result = await build({ entryPoints: ['entry.js'], bundle: true, format: 'iife', platform: 'browser', outfile: output + 'seeker-connect.js', metafile: true });
const packages = new Set();
for (const input of Object.keys(result.metafile.inputs)) {
  const match = input.match(/^(.*node_modules\/(?:@[^/]+\/)?[^/]+)\//);
  if (match) packages.add(match[1]);
}
const notices = [];
for (const dir of [...packages].sort()) {
  const pkg = JSON.parse(await readFile(path.join(dir, 'package.json'), 'utf8'));
  const files = (await readdir(dir)).filter(f => /^(license|copying|notice)/i.test(f));
  notices.push(`${pkg.name}@${pkg.version} — ${pkg.license}\n${pkg.repository?.url || ''}`);
  for (const file of files) notices.push(await readFile(path.join(dir, file), 'utf8'));
}
await writeFile(output + 'THIRD_PARTY_LICENSES.txt', notices.join('\n\n--------------------\n\n'));
console.log('Bundled official SDK and third-party licenses.');
