import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const site = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const repo = path.resolve(site, '..');
const manifestPath = path.join(site, 'translations/manifest.json');
const manifest = fs.existsSync(manifestPath) ? JSON.parse(fs.readFileSync(manifestPath, 'utf8')) : {};
const hash = text => crypto.createHash('sha256').update(text.replaceAll('\r\n', '\n')).digest('hex');
export const codeBlocks = text => [...text.matchAll(/^(`{3,}|~{3,})([^\n]*)\n([\s\S]*?)^\1\s*$/gm)].map(m => m[2] + '\n' + m[3]);
export const identifiers = text => [...text.replace(/^(`{3,}|~{3,})[^\n]*\n[\s\S]*?^\1\s*$/gm, '').matchAll(/`([^`\n]+)`/g)].map(m => m[1]).sort();
function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? walk(path.join(dir, e.name)) : [path.join(dir, e.name)]);
}
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  let errors = 0;
  let missing = 0;
  const accepted = process.argv.includes('--accept');
  for (const source of walk(path.join(repo, 'docs')).filter(f => f.endsWith('.md'))) {
    const relative = path.relative(repo, source).replaceAll('\\', '/');
    const translation = path.join(site, 'translations/ja', relative);
    if (!fs.existsSync(translation)) { console.log(`UNTRANSLATED ${relative}`); missing++; continue; }
    const english = fs.readFileSync(source, 'utf8').replaceAll('\r\n', '\n');
    const japanese = fs.readFileSync(translation, 'utf8').replaceAll('\r\n', '\n');
    const codeOK = JSON.stringify(codeBlocks(english)) === JSON.stringify(codeBlocks(japanese));
    const identifiersOK = JSON.stringify(identifiers(english)) === JSON.stringify(identifiers(japanese));
    if (!codeOK || !identifiersOK) { console.error(`CODE/IDENTIFIER DRIFT ${relative}`); errors++; continue; }
    if (accepted) manifest[relative] = hash(english);
    else if (manifest[relative] !== hash(english)) { console.error(`STALE ${relative}`); errors++; }
  }
  if (accepted) fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n');
  console.log(`Translation report: ${missing} missing, ${errors} stale/invalid. English remains authoritative.`);
  process.exitCode = errors || (missing && process.argv.includes('--strict')) ? 1 : 0;
}
