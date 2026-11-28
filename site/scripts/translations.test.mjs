import { test } from 'node:test';
import assert from 'node:assert/strict';
import { codeBlocks, identifiers } from './translations.mjs';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

test('detect code changes including identifiers inside code fences', () => {
  const en = '# Guide\n```java\nrun(1);\n```\nUse `configurationFile`.\n';
  const ja = '# ガイド\n```java\nrun(1);\n```\n`configurationFile`を使用。\n';
  assert.deepEqual(codeBlocks(en), codeBlocks(ja));
  assert.notDeepEqual(codeBlocks(en), codeBlocks(ja.replace('run(1)', 'run(2)')));
  assert.deepEqual(identifiers(en), identifiers(ja));
  assert.notDeepEqual(identifiers(en), identifiers(ja.replace('configurationFile', '設定')));
});

test('report missing, reject drift, accept reviewed source, then detect stale prose', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'sqlapp-translation-'));
  try {
    fs.mkdirSync(path.join(root, 'docs'), { recursive: true });
    fs.mkdirSync(path.join(root, 'site/scripts'), { recursive: true });
    fs.mkdirSync(path.join(root, 'site/translations/ja/docs'), { recursive: true });
    const script = path.join(root, 'site/scripts/translations.mjs');
    fs.copyFileSync(new URL('./translations.mjs', import.meta.url), script);
    const source = path.join(root, 'docs/guide.md');
    const translation = path.join(root, 'site/translations/ja/docs/guide.md');
    fs.writeFileSync(source, '# Guide\nUse `run`.\n```java\nrun();\n```\n');
    const run = (...args) => spawnSync(process.execPath, [script, ...args], { encoding: 'utf8' });
    assert.equal(run('--strict').status, 1);
    fs.writeFileSync(translation, '# ガイド\n`run`を使う。\n```java\nwrong();\n```\n');
    assert.equal(run('--accept').status, 1);
    fs.writeFileSync(translation, '# ガイド\n`run`を使う。\n```java\nrun();\n```\n');
    assert.equal(run('--accept').status, 0);
    assert.equal(run('--strict').status, 0);
    fs.appendFileSync(source, '\nNew behavior.\n');
    const stale = run();
    assert.equal(stale.status, 1);
    assert.match(stale.stderr, /STALE docs\/guide.md/);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});
