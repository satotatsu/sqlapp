import { defineConfig } from 'vitepress';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const origin = 'https://satotatsu.github.io';
const base = '/sqlapp/';
const sections = ['getting-started', 'schema', 'migration', 'data', 'gradle-plugin', 'development'];
const labels = {
  en: ['Getting started', 'Schema management', 'Database migration', 'Data management', 'Gradle plugin', 'Development'],
  ja: ['はじめに', 'スキーマ管理', 'データベース移行', 'データ管理', 'Gradle Plugin', '開発者向け']
};
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function theme(lang) {
  const prefix = lang === 'ja' ? '/ja' : '';
  return {
    nav: [
      { text: lang === 'ja' ? 'ガイド' : 'Documentation', link: `${prefix}/docs/README` },
      { text: lang === 'ja' ? '対応DB' : 'Databases', link: `${prefix}/docs/compatibility` },
      { text: lang === 'ja' ? 'デモ' : 'Live demos', link: `${prefix}/demos` }
    ],
    sidebar: [
      { text: lang === 'ja' ? 'リファレンス' : 'Reference', items: [
        { text: lang === 'ja' ? '対応DBと検証状況' : 'Compatibility and evidence', link: `${prefix}/docs/compatibility` },
        { text: lang === 'ja' ? '成果物と依存関係' : 'Artifacts and dependencies', link: `${prefix}/docs/artifacts` }
      ] },
      ...sections.map((section, i) => ({ text: labels[lang][i], collapsed: true,
        items: fs.readdirSync(path.join(root, 'docs', section)).filter(f => f.endsWith('.md')).sort((a, b) => a === 'README.md' ? -1 : b === 'README.md' ? 1 : a.localeCompare(b)).map(file => {
          const source = fs.readFileSync(path.join(root, ...(lang === 'ja' ? ['ja'] : []), 'docs', section, file), 'utf8');
          return { text: source.match(/^# (.+)$/m)?.[1]?.replace(/\s*\{#[^}]+\}$/, '') || file, link: `${prefix}/docs/${section}/${file.slice(0, -3)}` };
        })
      })),
      { text: 'Desktop GUI', link: `${prefix}/sqlapp-swing/README` }
    ],
    outline: { label: lang === 'ja' ? 'このページの内容' : 'On this page', level: [2, 3] },
    docFooter: { prev: lang === 'ja' ? '前のページ' : 'Previous page', next: lang === 'ja' ? '次のページ' : 'Next page' },
    returnToTopLabel: lang === 'ja' ? '先頭に戻る' : 'Return to top',
    sidebarMenuLabel: lang === 'ja' ? 'メニュー' : 'Menu',
    darkModeSwitchLabel: lang === 'ja' ? 'テーマ' : 'Appearance',
    langMenuLabel: lang === 'ja' ? '言語を変更' : 'Change language',
    socialLinks: [{ icon: 'github', link: 'https://github.com/satotatsu/sqlapp' }]
  };
}
export default defineConfig({
  title: 'sqlapp',
  description: 'Database Migration, Schema Management and Data Engineering Framework.',
  base,
  markdown: {
    anchor: {
      // Preserve GitHub's existing heading URLs, including numeric headings and
      // punctuation such as GRANT/REVOKE, instead of VitePress's default slugs.
      slugify: text => text.replace(/\[([^\]]+)\]\([^)]+\)/g, '$1').replace(/<[^>]+>/g, '').toLowerCase().replace(/[^\p{L}\p{N} _-]/gu, '').replace(/ /g, '-')
    },
    config(md) {
      const original = md.renderer.rules.fence;
      md.renderer.rules.fence = (tokens, index, options, env, self) => {
        if (tokens[index].info.trim() === 'mermaid') {
          return `<Mermaid code="${encodeURIComponent(tokens[index].content)}" />`;
        }
        return original(tokens, index, options, env, self);
      };
    }
  },
  srcExclude: ['public/**', '**/node_modules/**'],
  // VitePress assumes unknown file extensions are pages. Permit only real static
  // files; the output checker verifies their emitted links and the base path.
  ignoreDeadLinks: [url => {
    const relative = url.replace(/^\/sqlapp\//, '').replace(/^\//, '');
    const target = path.resolve(root, 'public', relative);
    const publicRoot = path.resolve(root, 'public') + path.sep;
    return target.startsWith(publicRoot) && [target, target + '.html'].some(f => fs.existsSync(f) && fs.statSync(f).isFile());
  }],
  locales: {
    root: { label: 'English', lang: 'en', themeConfig: theme('en') },
    ja: { label: '日本語', lang: 'ja', title: 'sqlapp', description: 'データベース移行・スキーマ管理・データエンジニアリングの統合フレームワーク。', themeConfig: theme('ja') }
  },
  themeConfig: {
    search: { provider: 'local', options: { miniSearch: { options: {
      tokenize: text => Array.from(new Intl.Segmenter('ja', { granularity: 'word' }).segment(text.normalize('NFKC')), item => item.segment).filter(word => /[\p{L}\p{N}]/u.test(word))
    } }, locales: { ja: { translations: {
      button: { buttonText: '検索', buttonAriaLabel: 'ドキュメントを検索' },
      modal: { noResultsText: '検索結果がありません', resetButtonTitle: '検索をクリア', footer: { selectText: '選択', navigateText: '移動', closeText: '閉じる' } }
    } } } } }
  },
  sitemap: { hostname: origin + base },
  transformHead({ pageData }) {
    if (pageData.relativePath === '404.md') return [];
    const route = pageData.relativePath.replace(/\.md$/, '.html').replace(/index\.html$/, '');
    const english = route.replace(/^ja\//, '');
    const url = origin + base + route;
    return [
      ['link', { rel: 'canonical', href: url }],
      ['link', { rel: 'alternate', hreflang: 'en', href: origin + base + english }],
      ['link', { rel: 'alternate', hreflang: 'ja', href: origin + base + 'ja/' + english }],
      ['meta', { property: 'og:title', content: `${pageData.title} | sqlapp` }],
      ['meta', { property: 'og:description', content: pageData.description || 'Database Migration, Schema Management and Data Engineering Framework.' }],
      ['meta', { property: 'og:type', content: 'website' }],
      ['meta', { property: 'og:url', content: url }]
    ];
  }
});
