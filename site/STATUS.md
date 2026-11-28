# Implementation and verification record

Recorded on 2026-11-28. **This work does not yet meet all requested completion
conditions.** Full translation and public deployment remain outstanding.

## Implemented

- VitePress 1.6.4 standard Home/theme, English default and Japanese locale.
- Mermaid 12.4.0 rendered client-side; Node >=22.12.0 required.
- Existing English Markdown stays at its GitHub paths. Staging is disposable;
  public Java APIs, versions and ordinary build tasks remain unchanged.
- Source-derived sidebar, local search with Japanese word segmentation, locale
  navigation, canonical/Open Graph/hreflang, sitemap and robots.
- One existing Compatibility Matrix; no duplicate manually maintained database lists.
- Pages build/PR workflow, isolated translation gate, npm lockfile and `npm ci`.
- Code/identifier/hash translation checks, HTML/SVG link/anchor/asset validation.
- English/Japanese Home, Getting Started and live-demo pages; seven complete
  Japanese translations of existing guides and section indexes.
- README entry points and Japanese README; unpublished source version distinguished
  from verified Plugin Portal release. No invented release download link.
- Reproducible HSQLDB documentation/schema-change/migration demos and embedded
  DuckDB SQL-model demo with an asserted CSV result and one model unit test.
- One-line `sqlapp-command` HTML template fix: hash navigation now reads the
  current tab container instead of an undefined variable. This was found during
  browser QA, tested and applied to regenerated examples. No DB behavior changes.

## Source evidence and limitations

Product descriptions were grounded in the root/docs indexes, Compatibility
Matrix, module READMEs, Gradle task registration, SQL-model command/tests and
existing offline examples. Broad per-dialect requalification was not performed.
The matrix's existing real-engine evidence is not a new test run from this work.
No Flyway/Liquibase/Atlas/dbt comparison is claimed.

The Plugin Portal page lists 0.30.0; source 0.80.0 was not verified as a published
plugin. The current-source API examples retain placeholders and an explicit
version notice. The offline quick start executes the checked-out implementation.
GitHub's public API returned default branch `main`, `has_pages: false`, and no
GitHub Releases. No usable account was returned by Git Credential Manager's
GitHub account listing. No credential was printed, stored or committed.
No remote setting, commit, push, pull request or deployment was performed.

## Translation status

The English docs tree contains 124 Markdown files. Seven have reviewed Japanese
counterparts; **117 remain untranslated**. Module READMEs also retain explicit
English fallbacks. These routes allow local navigation checks but do not satisfy
the requested initial English/Japanese content parity. The strict checker exits
1 for missing translations and currently prevents the Pages deployment job.
The independent library build/release process does not depend on that check.

## Verified commands and scope

- Java 25 repository Wrapper `demoSqlapp`: passed; HTML, SVG ER diagrams,
  before/after documentation, generated change SQL, matching migration and
  deliberately mismatching verification reports.
- Wrapper `--init-script site/demo.init.gradle demoSiteDataEngineering`: passed;
  two DuckDB models and one passing model unit test, CSV asserted as ALICE 30,
  BOB 7. Early driver/connection-ownership failures were corrected and rerun.
- Node 22.23.5 `npm ci`: passed after stopping preview to release Windows's
  esbuild lock. System Node installation was not modified.
- Node site tests: 4 passed, including negative link/fragment, stale/code-drift
  and real template hash-handler execution cases.
- VitePress build after clean installation: passed, including 47,688 emitted
  local HTML/SVG page, anchor and asset references. The same build/check passed
  again after refreshing the corrected template and demo outputs.
- `:sqlapp-command:test --tests com.sqlapp.data.db.command.html.GenerateHtmlDocsCommandTest`:
  9 passed, zero failures/errors/skips; `demoSqlapp` rerun passed afterward.
- Existing Python documentation checker: 893 links and 109 current pages passed.
- Existing Python documentation/HTML checker tests: 6 passed.
- Strict translations: exit 1, 117 missing, zero stale/code-invalid translations.
- Browser: English/Japanese Home, language switching, Japanese search results
  for 顧客, Mermaid SVG rendering, mobile menu, dark/light appearance and 1280px
  desktop layout verified. Actual generated ER SVG, customer columns/constraints
  and DDL tab rendered. Browser testing exposed the template error fixed above.
  After regeneration, Base → DDL hash navigation was verified again with zero
  new browser console errors.
  Preview must be restarted after rebuilding because it retains an asset inventory.

External databases, production data and containers were not accessed. Only
embedded HSQLDB and DuckDB were executed. Generated demo/site output is ignored,
not committed. Existing and concurrently changing user files were preserved.
A full Java test/build matrix and the remote GitHub Actions execution were not run.

## Remaining work and publication

Complete the 117 translations and module guides with semantic parity; preserve
code and identifiers, review source hashes and run the strict translation check.
The archived 20-table snapshot is intentionally preserved rather than regenerated;
its old template scripts have not received the new generator fix. Regenerate it
from the companion project before claiming the archived snapshot is fully qualified.
Data Generator and cross-vendor migration are documented workflows, but
no dedicated generated runs for them are included in this gallery.

After checks pass, commit only reviewed site/docs changes and merge them into
`main` through the repository's normal process. In repository Settings → Pages,
select GitHub Actions. Run Actions → Documentation if necessary, inspect build,
translation and deployment jobs, and verify the actual published URL:
https://satotatsu.github.io/sqlapp/ (expected, **not published by this task**).
Then set About/Website to that URL and review relevant database/migration topics.
Authenticated repository access is required for settings and publication.

See [README.md](README.md) for Windows-compatible setup, development, translation,
demo maintenance and deployment instructions.
