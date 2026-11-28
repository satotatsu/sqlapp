import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../.generated/.vitepress/dist');
function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? walk(path.join(dir, e.name)) : [path.join(dir, e.name)]);
}
let references = 0;
const errors = [];
const ids = new Map();
function anchors(file) {
  if (!ids.has(file)) ids.set(file, new Set([...fs.readFileSync(file, 'utf8').matchAll(/\bid=["']([^"']+)["']/g)].map(m => m[1])));
  return ids.get(file);
}
for (const file of walk(root).filter(f => /\.(html|svg)$/.test(f))) {
  const text = fs.readFileSync(file, 'utf8');
  if (/[A-Z]:\\Users\\|\/home\/runner\/work\/|jdbc:[^<>"\s]*password=/i.test(text)) errors.push(`Private path/connection in ${file}`);
  for (const match of text.matchAll(/\b(?:href|src)=["']([^"']+)["']/g)) {
    const href = match[1].replaceAll('&amp;', '&');
    if (/^(?:[a-z][a-z\d+.-]*:|\/\/)/i.test(href)) continue;
    const pathname = decodeURIComponent(href.split(/[?#]/)[0]);
    let target;
    if (pathname.startsWith('/')) {
      if (!pathname.startsWith('/sqlapp/')) { errors.push(`${file}: outside base ${href}`); continue; }
      target = path.join(root, pathname.slice('/sqlapp/'.length));
    } else target = pathname ? path.resolve(path.dirname(file), pathname) : file;
    if (pathname.endsWith('/')) target = path.join(target, 'index.html');
    references++;
    if (!fs.existsSync(target)) errors.push(`${file}: missing ${href}`);
    else if (/\.(html|svg)$/.test(target) && href.includes('#')) {
      const fragment = decodeURIComponent(href.slice(href.indexOf('#') + 1));
      if (fragment && !anchors(target).has(fragment)) errors.push(`${file}: missing anchor ${href}`);
    }
  }
}
if (errors.length) { console.error(errors.join('\n')); process.exitCode = 1; }
else console.log(`PASS: ${references} local HTML/SVG asset and page references under /sqlapp/.`);
