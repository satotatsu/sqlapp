# Documentation website

The site uses VitePress **1.6.4** (the stable npm release, rather than the 2.0
alpha). It is independent of the Java build. Use Node.js 22 and Java 25 with
the repository Gradle Wrapper. All commands below work on Windows; use
`.\gradlew.bat` instead of `./gradlew` in PowerShell.

From the repository root, generate the real demo outputs:

```shell
./gradlew --init-script docs/examples/offline-html/demo.init.gradle demoSqlapp
./gradlew --init-script site/demo.init.gradle demoSiteDataEngineering
cd site
npm ci
npm run dev
```

Open the printed URL under `/sqlapp/`. For production validation:

```shell
npm test
npm run translations
npm run build
npm run preview
```

`npm run build` always regenerates the staging tree and checks emitted local
HTML/SVG links and assets. It deliberately fails if demo artifacts are missing.
The Gradle demo asserts matching transfer and intentionally mismatching data.
It uses only fictional fixtures, private in-memory HSQLDB databases and embedded
DuckDB. The DuckDB demo checks two SQL models, a model unit test and the actual
aggregated output. It reuses the driver version declared by the DuckDB module;
there is no new library/module dependency or default task. These demos do not
test an external database or a cross-vendor migration.

## Source layout

- `../docs/`: canonical English guides, unchanged paths for GitHub readers.
- `pages/`: English and Japanese landing pages and live-demo catalog.
- `translations/ja/docs/`: Japanese counterparts with identical relative paths.
- `translations/manifest.json`: reviewed English SHA-256 hashes.
- `config.mjs`: standard VitePress theme, locales, navigation, local search and SEO.
- `scripts/`: staging, output verification and translation checks.
- `.generated/`: ignored staging, public demo artifacts and VitePress output.

Only the staging step rewrites repository-source links to GitHub links. English
content is never maintained in a second copy. The Compatibility Matrix remains
the only manually maintained database support inventory. Its evidence distinguishes
implemented capabilities, configured tests and recorded real-engine runs.

## Editing and translation

Edit English in `docs/` first. Translate to the matching path under
`site/translations/ja/`. Preserve **every fenced code block and inline code
identifier**, including their multiplicity. Preserve heading IDs used by existing
links: append explicit `{#original-english-id}` to translated headings. Check
internal links in the output and on GitHub. Never edit `.generated/`.

After reviewing the translation, run `node scripts/translations.mjs --accept`.
This records source hashes only for translations with matching code and identifiers.
Run `npm run translations` to detect stale translations or code drift; run
`npm run translations -- --strict` to also fail for missing translations.
Missing translations are reported and explicitly labeled on Japanese preview routes,
which display the English source until a complete translation is available.
Do not describe these fallback pages as translated. The strict report is the
completion check for full language parity. Neither check is part of Java builds
or library release tasks. Do not use `--accept` without reviewing semantic parity.
The independent strict translation job gates Pages deployment so the first
public bilingual release cannot silently ship English fallback guides.

To add another language, add a VitePress locale in `config.mjs`, extend staging
and translation checks for that locale, and add complete translated files and
localized landing pages. Do not generate empty placeholders. No browser-language
redirect is used. VitePress switches equivalent routes between configured locales.

## Demos and compatibility

Add reproducible offline examples beside `docs/examples/`, register opt-in tasks
in the demo init script, and explicitly allow their outputs in `prepare.mjs`.
Add links and scope statements to both demo catalogs. Keep sibling output
directories together because generated pages use relative links. Never publish
real data, credentials, developer paths or arbitrary build-directory contents.
Review JSON/SQL artifacts as well as HTML. Saved examples must be identified as
saved rather than regenerated. Update `docs/compatibility.md` from source and
recorded test evidence; never infer a passing engine run from a resolver class.

## Deployment and troubleshooting

The isolated `Documentation` workflow validates relevant pull requests and
builds/deploys relevant pushes to `main`, with manual dispatch. It uses `npm ci`,
Java 25, the wrapper, npm/Gradle caches and Pages artifacts. Only the deployment
job has Pages write/OIDC permissions; pull requests cannot deploy. Concurrent
deployments are serialized. Library publication is unaffected.

In GitHub repository **Settings → Pages → Build and deployment**, select
**GitHub Actions**. Merge the workflow and sources into `main`, then run
**Actions → Documentation → Run workflow** if needed. The expected URL is
<https://satotatsu.github.io/sqlapp/>. Set the repository Website to that URL
after successful publication. Review the deployment environment approvals if
the deploy job is waiting. Do not disable branch protection.

For a failing build, first check Java/Node versions, `npm ci`, then run the demo
task separately. Missing demo failures require demo generation, not fake output.
Dead links to static files must resolve to real files in staged `public/`;
do not globally disable link checks. Assets and demo links must remain under
`/sqlapp/`. Restart the preview server after rebuilding (its asset inventory is
cached). Stop it before `npm ci` on Windows to release esbuild's executable.
Check `npm run preview`, language switching, Japanese search, narrow
viewport, dark mode and actual demo links before publication. Inspect the Pages
workflow logs for server-only failures. Record browser and external-engine
checks separately from static link and compilation checks.
