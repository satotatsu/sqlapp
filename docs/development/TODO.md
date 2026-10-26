# Migration TODO

Updated: 2026-10-26.

## Session quoting policy

Verification: 129 selected tests across 17 modules passed, with no failures,
errors or skips. See the [verification record](sql-splitter-review.md#session-policy-verification-2026-10-25)
for the two execution scopes. The later DB2 follow-up also passed full test-source
compilation and all 257 module tests; the earlier compilation stall did not recur.

- [x] PostgreSQL: provide explicit initial `standardConformingStrings` policy;
  keep the existing modern default (`true`) and reject detectable SET/RESET,
  DISCARD ALL and relevant or indeterminate set_config calls with actionable
  configuration guidance.
- [x] MySQL / MariaDB: provide explicit initial `noBackslashEscapes` and
  `ansiQuotes` flags; reject sql_mode assignments, including executable comments,
  instead of guessing expression results or session state.
- [x] Keep settings fixed for each parse and retain the same policy when a
  splitter is reused. Shared callbacks contain no database-specific setting rules.

No open implementation items remain from this splitter review. Offline splitting
is not a SQL execution engine: it does not reconstruct transaction state or infer
session effects hidden in routines or dynamic SQL. Callers must supply the actual
initial quoting policy for each fixed-mode segment. The command/GUI preview uses
the documented defaults; advanced initial policies are available on the dialect
splitters. See the [policy and examples](sql-splitter-review.md#fixed-session-quoting-policy).

Full suites for all 17 reviewed modules passed: 5,245 tests including the previous
DB2 run, with zero failures, errors or skips. See the
[full-suite verification record](sql-splitter-review.md#full-module-suite-verification-2026-10-26).
External database verification remains unexecuted and requires explicit
authorization for its target and behavior.

## Completed SQL splitter work

Final follow-up verification: 122 selected tests across 17 modules passed, with
no failures, errors or skips. See the
[verification record](sql-splitter-review.md#additional-dialect-verification-2026-10-25)
for exact scopes, intermediate failures and remaining limits.

- [x] Protect inline comments and double-quoted tokens in the shared tokenizer.
- [x] SQL Server / Sybase: ignore GO inside strings, quoted identifiers and nested
  comments; preserve GO repetitions and reset state when a splitter is reused.
- [x] MySQL / MariaDB: protect backtick identifiers and default-mode escaped
  strings and DELIMITER directives.
- [x] HSQLDB: protect quoted identifiers and inline comments through the shared
  tokenizer.
- [x] Oracle: protect q/Q and nq/NQ alternative quotes, including slash lines
  inside anonymous-block literals, and report missing closing delimiters.
- [x] H2: protect dollar-quoted literals in ordinary SQL and ALIAS bodies.
- [x] SAP HANA: keep anonymous DO blocks together, including parameters, nested
  BEGIN, IF, WHILE, FOR and CASE constructs, quotes and comments. Reject missing
  block ends.


- [x] Fix the shared tokenizer consuming the first character after a delimiter
  when SQL statements are adjacent, including multi-character delimiters.
- [x] Add adjacent-statement regression tests in core, MySQL, DB2, Firebird and H2.
- [x] Review other dialect boundary strategies and run the selected splitter,
  PostgreSQL resolver, command inspection and Swing preview tests: 54 passed
  in the initial verification.
- [x] Update the Swing README and record compatibility boundaries, verification
  commands and remaining limitations in the [review record](sql-splitter-review.md).

## PostgreSQL anonymous DO block splitting

- [x] Fix anonymous `DO` blocks in the shared PostgreSQL SQL splitter.

Reproduction, currently checked by
[`MigrationSqlInspectionTest`](../../sqlapp-command/src/test/java/com/sqlapp/data/db/command/migration/schema/MigrationSqlInspectionTest.java):

```sql
DO $$ BEGIN PERFORM 1; PERFORM 2; END $$;
SELECT 3;
```

The fixed splitter returns two SQL parts: one
complete `DO` block followed by `SELECT 3`. This affects the offline editor's
boundary preview and any command/planning path using the same dialect splitter.
The preview currently exposes that output; it is not validation or execution approval.

Implementation location:
[`PostgresSqlSplitter`](../../sqlapp-core-postgres/src/main/java/com/sqlapp/data/db/dialect/postgres/util/PostgresSqlSplitter.java).
The shared dialect implementation now recognizes quoted tokens, including dollar
quotes, rather than special-casing CREATE FUNCTION. See the
[splitter review](sql-splitter-review.md) for version boundaries and other dialects.

Regression coverage:

- `$$` and tagged `$body$` bodies, optional language clauses and neighboring SQL.
- Internal semicolons, nested blocks, quoted strings and comments.
- Dollar-like text inside ordinary strings/comments, and missing closing tags.
- Existing `CREATE FUNCTION` behavior and supported older dialect resolution.
- The command inspection test asserts the corrected boundary count; the obsolete
  Swing README limitation has been removed.
- Parser-only regression tests remain separate from tests requiring a real database.

Initial verification: all 54 selected parser, resolver, command and preview tests passed.
See the [review record](sql-splitter-review.md#initial-verification-2026-10-25) for commands
and unexecuted scopes. No external database was used.
