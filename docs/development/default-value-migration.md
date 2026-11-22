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
dependency checks and transfer impact. Retain configuration and plan fingerprints
so decisions can be reproduced and invalidated when their inputs change.

Keep the following dimensions separate:

| Dimension | Values / meaning |
|---|---|
| Diagnostic severity | INFO, WARNING, UNRESOLVED; evaluation-point and time-basis differences default to WARNING; precision loss is reported |
| Execution decision | Proceed or blocked for the relevant phase, derived from policy and transfer requirements |
| Resolution | Open, corrected, or explicitly accepted with actor, timestamp and reason |
| Verification | Unchecked, statically checked, or checked against a specific database, with individual evidence |

Warnings do not block ordinary execution. An unresolved default need not block
unaffected transfer. `requireComplete()` must check for a usable candidate and
blocking issues for its stated purpose; it must not reject warnings automatically.
Existing unclassified issues initially remain unresolved until reviewed.

Static verification records which references resolve in the target Schema and
which remain external or unknown. Database verification records DDL application,
read-back comparison and value/temporal checks separately. Successful DDL alone
does not prove equivalent values; a successful INSERT is evidence only for the
conditions actually tested. Reopen resolutions when relevant fingerprints change.

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
product/version matrix established by implementation tests. Other expressions
remain visible as preserved or unresolved. Later work can add other dialects,
UUID/user/sequence functions, sequence-to-identity conversion, and reuse for CHECK
constraints or computed columns. General semantic DIFF normalization and UI support
are follow-ups; their absence must remain visible in verification results.

Schema XML and existing `Column` APIs remain unchanged. New public conversion
entry points and configuration names are not finalized here. Redesigning
`ColumnMigration.Result` is authorized for this unused migration API, but update
all repository callers and document the source compatibility impact. Other public
APIs and configuration formats retain compatibility.

## Required tests

- Core: source immutability, literal distinctions, semantic differences,
  deterministic ranking, overrides, unknown semantics, warning/strict behavior,
  valid candidates, report fingerprints and resolution invalidation.
- Dialects: actual reader representations, supported expressions, quoting,
  meaningful casts, legal defaults per column type, version boundaries and
  preserved-expression dependencies. Test local rules in their owning module.
- Commands: explicit values versus omission/DEFAULT/NULL, generated and identity
  columns, row-dependent supply, unsupported omissions, transfer decisions and
  cutover correction/acceptance records. Exercise existing assessment integration.
- Cross-dialect tests: conversion matrices in `sqlapp-core-dialect-test`, checking
  selected SQL, differences and target Schema rather than only SQL syntax.
- Authorized database tests: generate DDL, apply, read back and compare; INSERT
  while omitting the relevant column and check values, timezone and precision.
  Distinguish transaction and statement time with separate statements in one
  transaction; distinguish statement and call time within one statement with
  controlled evaluation delays. Avoid relying on coincidentally equal timestamps.

Run changed tests first, then affected modules and direct dependents. Keep real
database tests separate and require explicit authorization for their targets,
credentials and destructive behavior. No external database access is implied
by this design document.
