import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const site = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const repo = path.resolve(site, '..');
const out = path.join(site, '.generated');
// This directory contains only disposable site staging data.
fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(path.join(out, '.vitepress'), { recursive: true });
fs.copyFileSync(path.join(site, 'config.mjs'), path.join(out, '.vitepress/config.mjs'));
fs.cpSync(path.join(site, 'theme'), path.join(out, '.vitepress/theme'), { recursive: true });

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? walk(path.join(dir, e.name)) : [path.join(dir, e.name)]);
}
function write(relative, text) {
  const target = path.join(out, relative);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, text);
}
// Keep source paths for GitHub readers; convert repository-only links at staging time.
function renderSource(text, relative) {
  let fenced = false;
  return text.split('\n').map(line => {
    if (/^\s*(```|~~~)/.test(line)) { fenced = !fenced; return line; }
    if (fenced) return line;
    return line.replace(/<([\w-]+(?:-version|\.\.\.)|sqlapp-version|jdbc-driver-version)>/g, '&lt;$1&gt;')
      .replace(/(!?\[[^\]]*\]\()([^\s)]+)(\))/g, (all, start, href, end) => {
        const staticLink = url => start.startsWith('!') ? `${start}${url.replace(/^\/sqlapp\//, '/')}${end}` : `<a href="${url.replaceAll('&', '&amp;').replaceAll('"', '&quot;')}" target="_blank" rel="noopener">${start.slice(1, -2)}</a>`;
        if (href.startsWith('/sqlapp/')) return staticLink(href);
        if (/^(?:[a-z]+:|\/|#)/i.test(href)) return all;
        const [pathname, hash] = href.split('#');
        const resolved = path.posix.normalize(path.posix.join(path.posix.dirname(relative), pathname));
        const repositoryPath = resolved.replace(/^ja\//, '');
        const target = path.join(repo, repositoryPath);
        if (repositoryPath.startsWith('docs/examples/') && !pathname.endsWith('.md')) {
          return staticLink(`/sqlapp/${repositoryPath}${hash ? '#' + hash : ''}`);
        }
        if (!pathname.endsWith('.md') || !fs.existsSync(target) || repositoryPath.startsWith('site/')) {
          const url = `https://github.com/satotatsu/sqlapp/blob/main/${repositoryPath}${hash ? '#' + hash : ''}`;
          return `${start}${url}${end}`;
        }
        return all;
      });
  }).join('\n');
}
const sources = walk(path.join(repo, 'docs')).filter(f => f.endsWith('.md'));
for (const entry of fs.readdirSync(repo, { withFileTypes: true })) {
  if (entry.isDirectory() && entry.name.startsWith('sqlapp-')) {
    for (const name of fs.readdirSync(path.join(repo, entry.name)).filter(f => f.endsWith('.md'))) sources.push(path.join(repo, entry.name, name));
  }
}
for (const file of ['README.md', 'README.ja.md', 'CONTRIBUTING.md', 'LICENSE.md']) sources.push(path.join(repo, file));
for (const file of sources) {
  const relative = path.relative(repo, file).replaceAll('\\', '/');
  const original = fs.readFileSync(file, 'utf8');
  write(relative, renderSource(original, relative));
  const translated = path.join(site, 'translations/ja', relative);
  const japanese = fs.existsSync(translated) ? fs.readFileSync(translated, 'utf8') :
    `::: warning 翻訳状況\nこのページの日本語訳は未完了です。以下に英語の正本を表示しています。[英語版](/${relative.replace(/\.md$/, '.html')})を参照してください。\n:::\n\n${original}`;
  write('ja/' + relative, renderSource(japanese, 'ja/' + relative));
}
for (const file of walk(path.join(site, 'pages'))) {
  const relative = path.relative(path.join(site, 'pages'), file);
  write(relative, renderSource(fs.readFileSync(file, 'utf8'), relative.replaceAll('\\', '/')));
}
const publicDir = path.join(out, 'public');
fs.mkdirSync(publicDir, { recursive: true });
fs.cpSync(path.join(repo, 'docs/examples'), path.join(publicDir, 'docs/examples'), { recursive: true, filter: p => !p.includes('__pycache__') });
for (const name of ['demo', 'offline-demo', 'offline-change', 'offline-migration']) {
  const source = path.join(repo, 'build/docs', name);
  if (!fs.existsSync(path.join(source, name === 'offline-change' ? 'change.sql' : name === 'offline-migration' ? 'verification-match.json' : 'index.html'))) {
    throw new Error(`Missing ${name}. Run the repository demoSqlapp Gradle task first; see site/README.md.`);
  }
  fs.cpSync(source, path.join(publicDir, 'demos', name), { recursive: true });
}
fs.copyFileSync(path.join(repo, 'docs/examples/offline-html/catalog.xml'), path.join(publicDir, 'demos/catalog.xml'));
for (const name of ['customer-sales.csv', 'models.yaml', 'result.json']) {
  const source = path.join(repo, 'build/docs/site-engineering', name);
  if (!fs.existsSync(source)) throw new Error('Run the demoSiteDataEngineering task using site/demo.init.gradle first.');
  const destination = path.join(publicDir, 'demos/data-engineering', name);
  fs.mkdirSync(path.dirname(destination), { recursive: true });
  fs.copyFileSync(source, destination);
}
fs.writeFileSync(path.join(publicDir, 'robots.txt'), 'User-agent: *\nAllow: /sqlapp/\nSitemap: https://satotatsu.github.io/sqlapp/sitemap.xml\n');
fs.writeFileSync(path.join(publicDir, '.nojekyll'), '');
console.log(`Prepared ${sources.length} source documents, Japanese routes and actual demo artifacts.`);
