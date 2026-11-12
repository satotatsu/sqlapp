# Data migration UX and implementation priorities

[Development index](README.md) · [RDB transfer direction](rdb-transfer-workflow.md)

Updated: 2026-10-30. This document describes planned improvements, not new runtime
capabilities or a commitment to implement every option.

## Confirmed sqlapp baseline

- Direct JDBC, Access file entry, CSV packages, schema-only creation and optional
  Schema differences already use shared command services. Existing GUI/Gradle
  jobs, review, SQL drafts, warning policies and partial-progress evidence remain
  the foundation.
- `mappingFile` supports optional table/column name mapping for schema-only
  CREATE/differences and direct JDBC copy/verification, using the common Schema,
  target factories and existing bulk executor.
  GUI settings can select/drop the file; GUI and Gradle use the same saved job.
  CSV paths still reject it explicitly. The offline Tables/Columns editor in
  Job settings now saves the shared mapping without hand-written YAML. It starts
  from selected table identities and can load columns/informational types from
  captured metadata-only Schema XML without database access. Existing renames
  survive enrichment; type/value conversion remains unimplemented.
  Mapping grids provide literal search, sorting, renamed-only filtering and
  selected-name reset without changing hidden overrides or transfer selection.
  Input checks locate all syntax/duplicate-source issues using shared name rules;
  database resolution, target collision and FK checks remain in Review.
  A read-only YAML preview uses the save formatter, supports search/copy and
  invalidates generated content when names change. Previewing never writes files.
  The editor protects unsaved mapping intent on close and attaches saved mappings
  using job-relative references where path roots permit it.
  Check Schema now reuses execution's Schema projection against a bounded captured
  Schema and returns resolved names/PK/NULL/type metadata, warnings and fingerprints.
  The offline result does not claim current target state or authorize execution.
  An optional explicit product/version now previews column definitions through
  the target SQL builder over cloned mapped columns, retaining source key/NULL
  evidence and visible per-column preview limitations. The resolved builder is
  recorded separately; this is not live target metadata, a type override, or
  complete table-factory SQL. Existing inspection APIs/constructors and mapping
  fingerprints remain supported. Preview controls are not saved in job YAML.
  Schema check can navigate to the exact target-name editor cell using the
  shared projection's retained original mapping key, including short/qualified
  aliases. Missing display rows are populated without changing existing names;
  stale evidence is invalidated. Preview limitations retain bounded, single-line,
  Unicode-safe builder reasons rather than only exception categories.
  Multi-table checks now combine exact source-table selection, recorded preview
  issue filtering and literal search. Full-result availability counts remain
  distinct from visible-row counts. New/invalidated checks reset display scopes;
  copying and column editing keep their verified source identity, with no change
  to saved job scope or execution approval.
  Recorded preview issues now lead the selected-row detail text, without losing
  source metadata or changing table/TSV order. Plain displayed details can be
  copied with the existing UTF-8 size boundary and retry behavior. Model changes
  clear old column scopes/detail priorities; busy/empty selections cannot copy
  stale evidence. These remain presentation features rather than execution rules.
  Previous/next issue navigation now follows visible recorded preview issues in
  sort order, preserving table/search/issue scopes and horizontal position. It
  wraps, reports its visible position/no-match state and disables busy or stale
  navigation. The selected issue retains the same verified edit/copy path.
  Checked mappings can now be retained as standalone escaped HTML through shared
  command IO. Full/subset exports retain fingerprints, resolved builder, original
  keys, full-check warnings and explicit subset counts/order. GUI save captures
  exact view identities, rejects invalidated results and protects inputs/existing
  files; it writes bounded UTF-8 off the EDT. Reports may contain unsaved proposals
  and neither save jobs nor authorize execution. No task/YAML changes are required.
  Selected-target naming rules now support keep/upper/lower case and Unicode-space
  replacement with an explicit preview/apply step and guarded last-batch undo.
  Only resolved overrides are saved; execution and Gradle use the existing format.
  Naming previews can check a complete proposed mapping against captured Schema
  before applying. The check is asynchronous, retains warnings/failure evidence
  and rejects stale results while allowing draft refinement before database Review.
  Saved jobs can now reopen the shared table picker from Job settings using captured
  Schema, retain hidden selections and include referenced parents, then save the
  changed scope while preserving other options. CSV changes require a new export.
  Excluded mapping groups can be reviewed against captured Schema and explicitly
  removed from the name editor, preserving retained overrides/types. Identity
  resolution is shared with execution; unknown aliases and stale removal plans
  do not silently discard configuration.
- Shared Schema preparation now records per-table target identities and reviewed
  create/use/skip/difference intent in its immutable plan and fingerprint. Direct,
  schema-only and file import review use the same evidence. GUI table preparation
  is searchable and distinct from recorded SQL execution progress; it neither
  parses SQL nor adds per-table metadata queries. Existing Plan constructors and
  job YAML remain supported.
  Difference SQL now retains the generating table task ID instead of a shared
  generic task. Table preparation can navigate to an exact task in Schema
  statements, combining task selection and text search while preserving global
  order/status. Display filtering never changes execution scope or SQL order.
- Lower-level bulk configuration already has target-table identities and column
  mappings. Reuse those facilities where appropriate rather than create a
  separate GUI executor.
- Direct JDBC keyless INSERT now reuses lazy Table row iteration, shared chunk
  execution and externally sorted multiset-hash verification. Missing PKs warn;
  MERGE/resume/repair stay disabled. Optional missing-table creation can add a
  target-only generated PK, defaulting to `ID` with optional `generatedKeyColumn`
  in the shared job and both GUI authoring forms. CSV/TSV/JSONL keyless and text-key package import now uses full-scan INSERT with unordered multiset verification, empty-target checks and WARN/ERROR review policy. File packages can also opt into the same target-only generated PK creation, with default ID or an explicit generatedKeyColumn; source headers/values and original FK columns remain unchanged.
- The common settings form exposes target schema, Schema preparation, chunk
  size, verification and compatibility policy. More extraction/load controls
  require shared configuration and execution support first.

Implementation evidence:
[direct preparation](../../sqlapp-command/src/main/java/com/sqlapp/data/db/command/migration/transfer/TransferJobPreparationService.java),
[CSV export](../../sqlapp-command/src/main/java/com/sqlapp/data/db/command/migration/transfer/TransferFileExportService.java),
[CSV import](../../sqlapp-command/src/main/java/com/sqlapp/data/db/command/migration/transfer/TransferFileImportService.java),
[bulk resolver](../../sqlapp-command/src/main/java/com/sqlapp/data/db/command/migration/bulk/BulkMigrationJobConfigurationResolver.java),
[GUI settings](../../sqlapp-swing/src/main/java/com/sqlapp/swing/TransferJobSettingsPanel.java).

## Proposed sqlapp direction

These priorities follow sqlapp's existing product requirements. They are not
statements that the corresponding features are already implemented.

| Priority | Change | Required behavior |
|---|---|---|
| First | A clear guided journey | Source/target → tables and mappings → review/save/run. Keep detailed settings optional and allow reopening the saved job. Retain the dedicated MDB file selector and source-free CSV import. |
| First | Table and column mapping | Show source and target identities/types together. Default to existing same-name semantics; optional overrides must affect DDL, copy, verification and FK references consistently. |
| First | Per-table review states | Distinguish planned creation, existing-table use, exclusion, required decisions and warnings. Derive states from the saved policy and live review; a displayed state is not execution authorization. |
| Next | Bounded preview | Offer an explicit small sample with conversion findings. Keep it separate from full-data verification and avoid an automatic full row count merely to show a percentage. |
| Next | Consolidated execution results | Present Schema preparation, rows, verification, warnings and retained partial work in one journey using the current result/audit model. |
| Later | Optional naming/type rules | Preserve Japanese, quoted identifiers and original case by default. Preview changes, resolve identities unambiguously, detect collisions and retain the original model. |
| Later | Value conversion and tuning | Define reproducible shared semantics before GUI controls. Distinguish fetch size, JDBC batching and transaction chunks; transformations must retain type, NULL, identity and FK consistency. |

Extending name mapping to CSV and adding live target mapping review are the next
substantial gaps. Keep `mappingFile` rejection in each path until that path supports
mapping end to end, including copy and verification.
The common no-override job must stay concise. Generated artifacts retain resolved
identities, mappings and fingerprints, and edits invalidate the relevant review.

The GUI saves the authoritative job and required artifacts for both GUI and
Gradle. Avoid session-only mapping state, hidden global rules or GUI-only value
expressions. Compose the canonical Schema, target dialect factories, planners,
validators and existing executors; keep Gradle tasks as adapters.

Recreate/truncate, duplicate-error suppression, constraint disabling and native
bulk loading are separate advanced proposals. They need explicit intent and
honest transaction/verification semantics, not defaults inferred from a target's
existence. Preserve the user's try/inspect/refine policy: compatibility concerns
warn by default, strict policy can reject them, and actual execution failures
and integrity mismatches retain their failure evidence.

## Acceptance checks for mapping work

- Same/different products and direct/CSV paths interpret the same saved intent;
  CSV import works after the source is unavailable.
- Renamed tables/columns, composite keys and FKs remain consistent through DDL,
  copy and verification; exclusions with required references are explained.
- Original Schema and configuration are retained; ambiguous/colliding names,
  stale artifacts and unsupported settings are reported explicitly.
- GUI save/reopen and headless execution preserve behavior. Previews and saving
  never silently execute a transfer. Test embedded/model cases first; external
  database validation remains separately authorized.

Implementation status is stated separately from planned work. Performance and
database compatibility claims require their own execution evidence.

- Saved preview reports can be opened explicitly from the name editor, with a
  background fingerprint check, missing/changed-file diagnostics, retry support
  and a manual file path when browser integration is unavailable. Current edits
  remain separate from the last saved snapshot; opening grants no execution
  approval.

- Offline file-package previews now reuse import's typed reader and pinned Schema
  for CSV/TSV/JSONL, with table choice, 1..100 sampled rows, explicit NULL/empty
  display and bounded, marked cell truncation. Integrity hashing can read full
  member bytes; value parsing stays scoped to the sample. No database is opened,
  and full value validation/target review remain separate. Direct JDBC sampling
  and live target conversion previews are still outstanding.
