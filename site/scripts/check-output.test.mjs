import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

test('check base path, missing assets, fragment targets and relative demo navigation', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'sqlapp-output-'));
  try {
    fs.mkdirSync(path.join(root, 'scripts'), { recursive: true });
    const dist = path.join(root, '.generated/.vitepress/dist');
    fs.mkdirSync(path.join(dist, 'demos'), { recursive: true });
    const script = path.join(root, 'scripts/check-output.mjs');
    fs.copyFileSync(new URL('./check-output.mjs', import.meta.url), script);
    const page = path.join(dist, 'index.html');
    fs.writeFileSync(page, '<h1 id="start">Start</h1><a href="/sqlapp/demos/index.html">Demo</a>');
    fs.writeFileSync(path.join(dist, 'demos/index.html'), '<a href="../index.html#start">Home</a>');
    const run = () => spawnSync(process.execPath, [script], { encoding: 'utf8' });
    assert.equal(run().status, 0);
    fs.appendFileSync(page, '<img src="/sqlapp/missing.svg">');
    assert.match(run().stderr, /missing \/sqlapp\/missing.svg/);
    fs.writeFileSync(page, '<a href="/wrong/">Wrong base</a>');
    assert.match(run().stderr, /outside base/);
    fs.writeFileSync(page, '<a href="#missing">Bad fragment</a>');
    assert.match(run().stderr, /missing anchor/);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});
