# Data migration UX and implementation priorities

[Development index](README.md) · [RDB transfer direction](rdb-transfer-workflow.md)

Updated: 2026-10-29. This document describes planned improvements, not new runtime
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
  CSV paths still reject it explicitly. Type/value conversion and
  an interactive mapping editor remain unimplemented.
- Lower-level bulk configuration already has target-table identities and column
  mappings. Reuse those facilities where appropriate rather than create a
  separate GUI executor.
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

Extending name mapping to CSV and adding an interactive mapping editor are the next
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
