# Default value migration: design proposal

Updated: 2026-11-22. This document specifies planned behavior; it does not
describe an implemented feature or a finalized Java/YAML API.

## Goal and defaults

Make migrations between database products practical by automatically choosing
the target dialect's recommended default expression, including approximate
translations, and reporting semantic differences. Exact equivalence is an
optional requirement, not a prerequisite for ordinary migration.

- Continue with warnings and recommended approximate conversions by default.
- Collect all unsupported expressions rather than stopping analysis at the
  first one. Explain the affected columns and available remedies together.
- Allow transfer to proceed when omitted unsupported defaults cannot affect
  that transfer. Check remaining issues before cutover.
- Offer strict mode for users who require exact conversions. Analysis still
  completes; strict mode prevents applying the affected plan.
- Never hide an omitted default or silently treat an unknown expression as a
  known function or literal.

## Existing implementation and affected modules

The existing implementation provides starting points:

- `sqlapp-core`: `Column.defaultValue` stores SQL text; `Catalog` can serialize
  product name and version. `ColumnMigration` delegates conversion to a target
  dialect and has a narrow native TIMESTAMP conversion helper. Its current
  `Result` has string issues, and `requireComplete()` rejects every issue.
- MySQL readers already quote literals and distinguish generated default
  expressions. Parsing must consume the Schema representation produced by
  readers, not reinterpret raw JDBC catalog values.
- `SchemaCompatibilityAnalyzer` currently compares default strings.
- Assessment's `MigrationTargetDdlGenerator` currently emits only target
  defaults explicitly supplied by mappings. Automatic migration must integrate
  deliberately with this path rather than changing its behavior incidentally.
- PostgreSQL's `PostgresUtils` can populate `identity` and `sequence` as well
  as retaining a serial column's `nextval(...)` default. Consume those fields
  together; do not classify an already modeled generator as an unrelated unknown
  default solely because its SQL contains `nextval`.

Shared conversion concepts and orchestration belong in `sqlapp-core`;
product-specific recognition, rendering and capability checks belong in the
corresponding `sqlapp-core-{db}` modules. Configuration files, reports and
transfer/cutover integration belong in `sqlapp-command`. Gradle tasks expose
properties and invoke commands. Optional UI filtering can consume the report
later; it is not a dependency of the first implementation.

No module dependencies are proposed here. Prefer extending the existing column
migration path over introducing a parallel type/default conversion pipeline.

## Schema preservation and conversion flow

Keep the source expression in the existing `Column.defaultValue`. Preserve the
source Schema/XML; create a separate target Schema using existing model cloning
and ownership conventions. Do not add a serialized common expression field or
`${...}` notation. Existing readers may already normalize source SQL; preserving
the source means preserving their Schema representation, not original DDL bytes.

```text
Source Schema/XML + source dialect + target dialect + optional overrides
  -> target column type conversion
  -> preserve or translate defaults
  -> target Schema + structured conversion report
  -> existing SQL factories / transfer planning / cutover checks
```

Resolve the source dialect from available product/version metadata. Allow an
explicit source dialect when metadata is missing. If it cannot be resolved,
report affected defaults as unresolved rather than guessing. Basic operations
not requiring that resolution may continue.

For the same product, prefer retaining the original expression. Check target
version capabilities, renamed references and available dependencies where
possible. Preserve and report unknown dependencies without claiming execution
was verified. An identified incompatible expression follows the conversion or
unsupported-expression path. Product-family compatibility is a dialect decision,
not a product-name or increasing-version shortcut.

## Internal expression model

Use a conversion-local common representation for basic literals and recognized
current-date/time expressions. It is not a new Schema XML contract. Retain raw
SQL for unrecognized expressions and for diagnostics.

Current-time semantics distinguish:

- Evaluation point: transaction start, statement start, actual call time, or
  unknown/product-specific behavior.
- Time basis: session zone, database server OS time, UTC, or unknown; retain
  relevant environment requirements where known.
- Result kind: date, time, datetime, and their applicable timezone variants.
- Fractional precision: explicit precision, known product default, or unknown.

Do not equate SQL type names across products without type conversion. For
example, a source value containing time can lose time when stored in a target
date-only column. Model function translation and target-column coercion as
separate steps, and report both.

Recognize a bounded set of token patterns, including necessary parentheses,
quoting and known casts. Preserve meaningful casts. Do not infer BOOLEAN from
arbitrary strings such as `'Y'`. Distinguish no default, SQL NULL, and a string
literal containing `NULL`. Parsing must not execute source expressions.

## Candidate selection and optional configuration

First filter candidates to expressions legal as defaults for the target column
and target version. Keep product/version restrictions in their dialect modules.
Then select automatically using a common deterministic preference order that
dialects can override:

1. Preserve result kind.
2. Preserve time basis.
3. Preserve evaluation point.
4. Preserve precision, preferring no precision loss.

Prefer an exact match whenever available. The ordering is a default heuristic,
not a proof of equivalence; dialects define stable tie-breaking and recommended
alternatives. Unknown semantics must not be classified as exact.

Reuse `Dialect.getCurrentDateFunction()`, `getCurrentTimestampFunction()` and
related existing definitions as candidate sources where their semantics fit.
Keep their `DbDataType.defaultValueLiteral` consumers consistent with the same
dialect definitions. Do not create an independently maintained duplicate function
table or assume those convenience methods alone describe every temporal semantic.
Preserve existing method behavior while introducing the richer candidate metadata.

Ordinary use requires no per-column approvals. Optional controls include:

- Strict mode rejecting approximate or unresolved conversions at application.
- Difference-specific policy for warning handling and strict-mode exceptions.
- Function mappings, for example treating a particular user function as a
  specified common expression, with its configured meaning recorded.
- Column-specific target SQL replacement or explicit removal.

Precedence is column override, explicit function mapping, then standard dialect
conversion. Policies evaluate the resulting differences; they do not erase them.
Reject ambiguous or conflicting overrides with actionable diagnostics. Distinguish
unspecified, replacement and explicit removal without overloading blank strings.
Reuse assessment's column default override where compatible with these semantics.

## Report and decision records

Report each applicable default as preserved, converted exactly, converted
approximately, or requiring action. Record explicit user removal separately as
an action so it cannot be confused with an unsupported omission or absent default.

Include resolved source/target column identities, original/target expressions,
products and versions, semantic differences, selection reason, applied rule IDs,
dependency checks and transfer impact. First delivery emits these facts in a
structured report without requiring a persistent decision store. In the later
cutover phase, retain configuration and plan fingerprints so saved decisions can
be reproduced and invalidated when their inputs change. Existing artifact
fingerprint requirements continue to apply; this feature adds no first-phase
decision-persistence subsystem.

Keep the following dimensions separate:

| Dimension | Values / meaning |
|---|---|
| Diagnostic severity | INFO, WARNING, UNRESOLVED; evaluation-point and time-basis differences default to WARNING; precision loss is reported |
| Execution decision | Proceed or blocked for the relevant phase, derived from policy and transfer requirements |
| Resolution (later cutover phase) | Open, corrected, or explicitly accepted with actor, timestamp and reason |
| Verification | Unchecked, statically checked, or checked against a specific database, with individual evidence |

Warnings do not block ordinary execution. An unresolved default need not block
unaffected transfer. `requireComplete()` must check for a usable candidate and
blocking issues for its stated purpose; it must not reject warnings automatically.
Existing unclassified issues initially remain unresolved until reviewed.

Static verification records which references resolve in the target Schema and
which remain external or unknown. Database verification records DDL application,
read-back comparison and value/temporal checks separately. Successful DDL alone
does not prove equivalent values; a successful INSERT is evidence only for the
conditions actually tested. In the later cutover phase, reopen saved resolutions
when relevant fingerprints change.

## Serial columns and existing generators

Include a narrow PostgreSQL serial-to-target-identity path in first delivery.
Prefer existing Schema identity/sequence metadata over reconstructing it from
SQL text. Recognize the associated `nextval` as part of that generator and avoid
emitting both an identity and a separate unresolved DEFAULT.

Automatic conversion requires an unambiguous column-owned sequence, no known
shared or external consumers, and target support for the required generator
behavior. Sequence names or a `nextval` pattern alone do not establish ownership.
Use existing metadata where sufficient; if additional ownership information is
needed, obtain it in the PostgreSQL reader in bulk, without per-column queries.
Record any differences in generator options or explicit-value insertion behavior.

Reuse the existing next-value convention: `Sequence.lastValue` and corresponding
identity state represent the next value to issue, not the last value returned.
Carry start, increment and relevant bounds through the existing generator model.
Do not advance the source generator to discover its state. Explicit-value loading
may not advance a target generator: plan restoration/reseeding after transfer
using existing facilities, and report missing state, collisions or unsupported
restoration rather than inventing a next value. Capture/restore timing must account
for source writes; a stale metadata snapshot is not a cutover guarantee.

Where this narrow conversion cannot be established, keep the issue unresolved
but group it by a stable generator reason and supported remedy, with affected
columns listed together. Batch overrides must remain available. General shared
sequence translation is deferred, rather than silently converted to per-column
identities. The same-product preservation path remains preferred.

## Unsupported defaults, transfer and cutover

In ordinary mode, generated migration DDL may omit an unsupported DEFAULT and
include a human-readable comment, while retaining the original expression and
omission in the structured report. Escape source text safely in SQL comments.
Use the existing SQL factories against the target Schema.

Determine transfer impact from the resolved column mapping and actual execution
semantics, including generated/identity columns, transformations, omitted columns,
DEFAULT tokens and per-row omission behavior. Binding SQL NULL is not equivalent
to requesting DEFAULT. A mapping entry alone does not prove a value is supplied
for every row.

- Proven independent of the omitted default: allow transfer and keep a cutover
  issue open.
- Requires that default: block the affected transfer until a value, expression
  or other valid remedy is supplied.
- Cannot determine independence: report the uncertainty and require a concrete
  decision before executing the affected transfer.

Accepting a cutover issue does not make an otherwise invalid transfer executable.
Analysis and unaffected work can continue. Cutover checks open issues and requires
correction or explicit acceptance, keeping the details accessible to callers.
Automated cutover decision persistence is a later phase. First delivery lists
these remaining obligations in the report and does not claim cutover readiness
or require a new acceptance store to perform unaffected transfer.

## DIFF and verification

DIFF does not perform dialect translation. Compare the translated target Schema
with the actual target database for application verification and rerun planning.

For supported expressions, compare their semantics using the same target dialect
and environment assumptions, including meaningful casts and precision. This is
separate from selecting an acceptable approximate translation between products.
For unknown expressions retain conservative string comparison and report uncertain
differences. Do not normalize inside literals or quoted identifiers indiscriminately.
Do not assume an accepted approximation makes two different expressions equal.

## Scope, compatibility and delivery

First delivery covers basic literals and current-date/time functions for Oracle,
SQL Server, PostgreSQL, MySQL/MariaDB and SQLite, with an explicit supported
product/version matrix established by implementation tests, plus the narrow
owned-serial generator path above. Deliver conversion, a structured report and
transfer-impact decisions first. Persistent correction/acceptance records,
decision fingerprints and invalidation, and automated cutover checks form the
next phase; they are not prerequisites for first delivery.

Other expressions remain visible as preserved or unresolved. Later work can add
other dialects, UUID/user/general sequence functions, broader sequence-to-identity
conversion, and reuse for CHECK constraints or computed columns. General semantic
DIFF normalization and UI support are follow-ups; their absence must remain
visible in verification results.

MySQL `ON UPDATE CURRENT_TIMESTAMP` is separate from DEFAULT conversion. First
delivery preserves it where the target supports its semantics; otherwise report
it as requiring action, with a trigger or application change as possible remedies.
Do not generate a trigger automatically or silently discard existing issues.

Schema XML and existing `Column` APIs remain unchanged. New public conversion
entry points and configuration names are not finalized here. Redesigning
`ColumnMigration.Result` is authorized for this unused migration API, but update
all repository callers and document the source compatibility impact. Other public
APIs and configuration formats retain compatibility.

## Required tests

- Core: source immutability, literal distinctions, semantic differences,
  deterministic ranking, overrides, unknown semantics, warning/strict behavior,
  valid candidates and structured reports. Decision fingerprints and resolution
  invalidation are tested with the later cutover phase.
- Dialects: actual reader representations, supported expressions, quoting,
  meaningful casts, legal defaults per column type, version boundaries and
  preserved-expression dependencies. Test local rules in their owning module.
- Commands: explicit values versus omission/DEFAULT/NULL, generated and identity
  columns, row-dependent supply, unsupported omissions, transfer decisions and
  remaining cutover obligations. Exercise existing assessment integration;
  correction/acceptance persistence tests belong to the later phase.
- Generators: modeled serial identity plus `nextval` is handled once; test owned
  versus shared/ambiguous sequences, quoted names, non-unit increments, unknown
  next state, explicit-value transfer and post-load restoration without consuming
  source values. Test unsupported target options and grouped unresolved reports.
- ON UPDATE: preserve supported behavior and report unsupported behavior separately
  from the DEFAULT result.
- Cross-dialect tests: conversion matrices in `sqlapp-core-dialect-test`, checking
  selected SQL, differences and target Schema rather than only SQL syntax.
- Authorized database tests: generate DDL, apply, read back and compare; INSERT
  while omitting the relevant column and check values, timezone and precision.
  Distinguish transaction and statement time with separate statements in one
  transaction; distinguish statement and call time within one statement with
  controlled evaluation delays. Avoid relying on coincidentally equal timestamps.
- SQLite: compare values produced by omitted-column defaults with values written
  by sqlapp's date/time converters under the repository's ISO-8601 storage policy.
  Check UTC/offset handling, separators, fractional precision and read-back, rather
  than assuming a built-in current-timestamp expression uses the required format.

Run changed tests first, then affected modules and direct dependents. Keep real
database tests separate and require explicit authorization for their targets,
credentials and destructive behavior. No external database access is implied
by this design document.

## Implementation status

Updated: 2026-11-27. Core foundation and the SQL Server to PostgreSQL path.

Implemented:

- `com.sqlapp.data.db.dialect.defaultvalue`: conversion-local expression model
  (`DefaultExpression`, `TemporalSemantics`), a bounded tokenizer, the
  `DefaultExpressionSupport` dialect extension point with
  `StandardDefaultExpressionSupport`, and `DefaultValueMigration`
  (same-product preservation, literal and current date/time translation,
  candidate ranking by time basis, evaluation point and precision, column
  coercion reporting, strict mode, column overrides).
- `ColumnMigration.Result` carries `MigrationDiagnostic` (INFO, WARNING,
  UNRESOLVED) and the default result. `requireComplete()` blocks only on
  UNRESOLVED findings or a missing candidate. String issues from existing rules
  remain UNRESOLVED. New `ColumnMigration.convert(source, sourceDialect, target)`
  converts the type with the target's rule when one exists, then the default;
  the existing two-argument `convert` is unchanged.
- `Dialect.getDefaultExpressionSupport()`, implemented for SQL Server
  (`SqlServerDefaultExpressionSupport`) and PostgreSQL
  (`PostgresDefaultExpressionSupport`), using the forms their readers return.
  PostgreSQL serial `nextval` defaults are recognized as generator defaults and
  reported as requiring action until generator conversion exists.

Verified: unit tests in core, SQL Server and PostgreSQL modules, and
`SqlServerToPostgresDefaultMigrationTest` (dockerTest): defaults read from SQL
Server 2022 are converted, the table is created on PostgreSQL 17, inserted
values are checked, statement-start evaluation is checked within one
transaction, and the stored PostgreSQL defaults are recognized again.

Reuse beyond defaults: tokens keep their source positions and comments are
skipped, `DefaultExpressionTokens.replace` rewrites only recognized ranges,
`DefaultExpressionSupport.recognize` accepts a token range with no column, and
`TemporalTranslator` selects a target expression by expected kind (or the
source kind) without a column. These are the extension points for CHECK
constraints, computed columns and, together with AI-assisted logic migration,
routine and trigger bodies. Finding calls inside longer expressions and
mappings for non-temporal functions with argument rewriting are not
implemented.

Transfer integration (2026-11-28): `TransferSchemaPreparation` converts the
defaults of tables it creates or changes, using the source dialect resolved
from the Schema product metadata, before generating target SQL. The source
Schema keeps its expressions. `Plan.defaults()` holds a
`TransferDefaultConversion` per column (outcome, expressions, differences,
verification, transfer impact, diagnostics); warnings carry the same findings
prefixed `[SCHEMA_DEFAULT_CONVERTED]` or `[SCHEMA_DEFAULT_UNRESOLVED]`, and
`TransferSchemaChangeChecks.openDefaultCount` counts open ones. Bulk copies map
every column one to one and bind every value, so transfer impact is
INDEPENDENT for copied tables and NOT_APPLICABLE for schema-only preparation;
open defaults are omitted from the target SQL and remain cutover obligations.
APPLY_DIFFERENCES keeps an existing target DEFAULT whose recognized meaning
equals the planned one, so normalized spellings are not reported as changes.
A PostgreSQL serial `nextval` default whose column stays an identity is
reported as GENERATOR (handled by the identity); next-value restoration uses
the existing generated-key checks.

Not yet implemented: Oracle, MySQL/MariaDB and SQLite supports, broader
sequence conversion, an explicit source-dialect setting for Schemas without
product metadata, configuration of overrides, strict mode and function
mappings in transfer jobs, and a REQUIRES_DEFAULT impact for paths that omit
columns (none exist in transfer today).
PostgreSQL DATE candidates use `CURRENT_DATE` rather than the existing
`getCurrentDateFunction()` text, whose semantics are the same.
