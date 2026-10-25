# SQL splitter review

## PostgreSQL

Dollar quoting is lexical syntax available since PostgreSQL 8.0; anonymous DO
blocks are available since 9.0. References: [8.0 string syntax](https://www.postgresql.org/docs/8.0/sql-syntax.html)
and [9.0 DO](https://www.postgresql.org/docs/9.0/sql-do.html).
The resolver remains unchanged, including its older-version fallback behavior.
Parser tests cover resolver selections for 8.0, 8.2, 8.4, 9.0, 9.6, 15 and 18;
DO cases apply to 9.0 and later. Splitting does not validate server capabilities.

The PostgreSQL tokenizer now marks dollar quotes, ordinary strings, E strings,
quoted identifiers and comments in source order. Semicolons inside those ranges
cannot split a statement. Dollar tags are case-sensitive; a tag embedded in an
identifier does not open a literal. Block comments may nest. Missing closing
quotes, tags and block comments produce errors rather than partial SQL boundaries.
The existing CREATE FUNCTION fixture remains a regression test. This also covers
CREATE PROCEDURE bodies and dollar literals in expressions without adding
statement-specific regular expressions.

Ordinary strings use doubled quote escaping by default; explicit E strings
additionally support backslash escapes. Non-default initial quoting is available
through the fixed-session policy described below. PostgreSQL-derived dialects
that inherit this splitter also inherit the lexical fix; that does not establish
support for DO on every derived database.

## Other dialects

Review of the shared tokenizer found an off-by-one error: after a delimiter it
consumed one additional character. `SELECT 1;SELECT 2` therefore lost the second
SELECT's S. The shared fix preserves that character, including for custom
multi-character delimiters. Core and MySQL, DB2, Firebird and H2 tests cover
adjacent statements. No dialect resolution, SQL factory or metadata API changes
are needed. Protected tokenizer hooks provide quote/comment recognition and marker
registration without requiring new public configuration.

| Dialect | Boundary strategy reviewed |
| --- | --- |
| MySQL / MariaDB | DELIMITER directives; MariaDB inherits MySQL behavior |
| DB2 | --#SET TERMINATOR directives |
| Firebird | SET TERM directives |
| SQL Server / Sybase | GO batches and repetition counts; a part is a batch |
| Oracle | Slash-terminated anonymous PL/SQL and stored-program handling |
| HSQLDB | Compound routine block tracking |
| SQLite | Dedicated trigger and quoted-token scanner |
| H2 | Dollar-quoted literals in ordinary SQL and ALIAS bodies |
| SAP HANA | CREATE/ALTER handling plus anonymous DO block tracking |
| Vertica | CREATE/ALTER block recognition |
| Spanner | Shared semicolon tokenizer |

The common scanner recognizes quoted tokens and comments in source order. Inline
comments protect their semicolons, and double-quoted tokens are opaque. Dialects
add quote forms and comment rules using protected hooks rather than placing
vendor conditions in core. Existing protected entry points remain present; no
serialized model or dependency changes are required. Initial quoting policies
are optional additive properties on the owning dialect splitters.

SQL Server and Sybase ignore GO inside quoted tokens (including bracketed
identifiers) and nested comments. GO repetition uses a stable batch snapshot;
per-parse state is reset so the same splitter can be reused. MySQL and MariaDB
recognize backtick identifiers, backslash escapes in ordinary/double-quoted
strings and inline # comments. MySQL -- comments require following whitespace.
The unused legacy --//@UNDO convention is not treated as a special directive;
old fixtures now use ordinary MySQL comments.
DELIMITER text inside quoted strings does not change the delimiter.

Oracle q/Q and nq/NQ quotes support paired [], (), {} and <> delimiters and arbitrary
non-whitespace delimiters. H2 dollar quoting is lexical, including outside ALIAS
statements. Missing closing quotes, dollar delimiters and block comments fail
before any partial split result is returned. SAP HANA anonymous DO blocks retain
parameters, nested BEGIN/END, IF, WHILE, FOR and CASE constructs; their quotes and
comments use the common scanner. Missing block ends are reported.

The changes apply to each dialect's existing splitter entry point; resolvers
remain unchanged. Parser regression cases cover H2 1.4/2.0/2.2/2.3, Oracle 9
ordinary strings and 10g/11g/19c/23ai alternative quotes, MariaDB 10/11/12 and
SAP HANA 1 ordinary SQL plus HANA 2/Cloud anonymous blocks. Splitting is not a
server capability validator. HANA anonymous-block syntax is documented in the
[HANA 2.0 SQLScript reference](https://help.sap.com/doc/6254b3bb439c4f409a979dc407b49c9b/2.0.00/en-us/sap_hana_sql_script_reference_en.pdf),
H2 dollar literals in the [H2 grammar](https://h2database.github.io/html/grammar.html),
and Oracle alternative quotes in the [Oracle reference](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/Literals.html).

This is not exhaustive SQL grammar validation. Existing CREATE/ALTER block
heuristics and client directives still have limits. SQL execution effects,
transaction state, routines and dynamic SQL are not interpreted; quoting must
remain fixed within each input segment.

No external database is used, and no metadata queries, dependencies, public
configuration or generated migration artifacts are changed.

## Initial verification (2026-10-25)

Java 21 and the repository Gradle Wrapper completed the following selected
offline scopes successfully (54 tests, zero failures/errors in the resulting
JUnit reports):

- `:sqlapp-core:test --tests '*SqlSplitterTest'`
- `:sqlapp-core-{postgres,mysql,db2,firebird,h2,hsql,oracle,sqlserver,sybase,sqlite,spanner,saphana,virtica}:test --tests '*SqlSplitterTest'`
  (each module was passed as an individual Gradle task).
- PostgreSQL additionally: `--tests '*resolver.DialectResolverTest'`.
- `:sqlapp-command:test --tests '*MigrationSqlInspectionTest'`.
- `:sqlapp-swing:test --tests '*SqlBoundaryPreviewTest' --tests '*SqlSplitPreviewPanelTest'`.

The focused PostgreSQL splitter suite was run first. Initial attempts encountered
an invalid default JAVA_HOME, an incremental compilation failure, a transient
Sybase class-loading failure and an incorrectly encoded new Unicode test fixture.
A valid local Java 21 runtime, a full recompilation, a rerun and UTF-8 source
encoding resolved these issues; the final combined invocation was successful.
No full module suites or external database tests were run. No generated files
were intentionally changed; build outputs were produced only by Gradle checks.

## Additional dialect verification (2026-10-25)

Final result: BUILD SUCCESSFUL; 122 tests across 17 modules, with zero failures,
errors or skips in the isolated JUnit reports.

The follow-up run covers the reproduced non-PostgreSQL failures, default and
legacy resolver paths, incomplete lexical tokens, empty input and the existing
whole-line comment extension point. The selected offline scopes are:

- `:sqlapp-core:test --tests 'com.sqlapp.data.db.dialect.util.*Test'`.
- Each of `mysql`, `mariadb`, `h2`, `hsql`, `sqlserver`, `sybase`, `oracle`,
  `saphana`, `postgres`, `db2`, `firebird`, `sqlite`, `spanner` and `virtica`:
  `:sqlapp-core-<module>:test --tests '*SqlSplitter*Test' --tests '*resolver.*Test'`.
- `:sqlapp-command:test --tests '*MigrationSqlInspectionTest'`.
- `:sqlapp-swing:test --tests '*SqlBoundaryPreviewTest' --tests '*SqlSplitPreviewPanelTest'`.

All commands use the repository Gradle Wrapper and a local Java 21 runtime.
Normal build outputs encountered disappearing class files during verification;
the final run uses a generated init script and a separate project cache to put
outputs under `build/splitter-verification/outputs/<project>`. Core compilation
is forced without incremental compilation for the final verification. These are
verification-only outputs, not repository build-setting changes.

Intermediate runs caught an incorrect whitespace expectation in a new test,
obsolete --//@UNDO fixtures, the need to preserve the legacy whole-line comment
hook and an empty-input null-line regression. The fixtures were converted to
ordinary comments, the scanner was corrected, and explicit regressions were
added. No external database tests or full module suites were run. Existing
SQLite recreation tests use only an embedded database. No generated migration
artifacts were intentionally changed.

## Fixed session quoting policy

A parse uses one explicit initial quoting policy. Defaults preserve the previous
behavior: PostgreSQL ordinary strings use standard quoting; MySQL/MariaDB ordinary
strings use backslash escapes and double quotes are strings. Native PostgreSQL
changed its default to standard quoting in 9.1; legacy or otherwise non-default
sessions must configure the initial policy rather than relying on a version-only
guess. See [PostgreSQL 9.1 changes](https://wiki.postgresql.org/wiki/What%27s_new_in_PostgreSQL_9.1)
and [MySQL string literals](https://dev.mysql.com/doc/refman/8.0/en/string-literals.html).

For a non-default, fixed-mode segment:

```java
MySqlSqlSplitter mysql = (MySqlSqlSplitter) mysqlDialect.createSqlSplitter();
mysql.setNoBackslashEscapes(true); // NO_BACKSLASH_ESCAPES
mysql.setAnsiQuotes(true);        // ANSI_QUOTES
var mysqlParts = mysql.parse(sql);

PostgresSqlSplitter postgres = (PostgresSqlSplitter) postgresDialect.createSqlSplitter();
postgres.setStandardConformingStrings(false); // legacy/non-default escaping
var postgresParts = postgres.parse(sql);
```

The matching `isNoBackslashEscapes()`, `isAnsiQuotes()` and
`isStandardConformingStrings()` getters expose the configured policy. Configuration
persists across repeated parse calls; parsing does not change these properties.
Explicit PostgreSQL E strings retain backslash escaping under either policy.
MySQL double-quoted identifiers under ANSI_QUOTES and backtick identifiers do not
use string backslash escapes.

The splitter rejects recognized sql_mode assignments (including multiple-variable
SET statements and MySQL/MariaDB executable comments), PostgreSQL SET/RESET of
standard_conforming_strings, RESET ALL and DISCARD ALL. Detection also rejects
set_config calls targeting that setting, or whose target is not a plain,
unescaped constant string. Known calls for unrelated settings remain allowed.
Comments, string literals and quoted identifiers containing similar text do not
constitute setting changes. Executable-comment version conditions are not
executed: potential mode assignments in those comments are rejected conservatively.

The error instructs the caller to split the script into fixed-mode segments and
configure each segment's actual initial mode. Handle the mode-changing statements
separately; configuring an initial mode does not opt into interpreting those
statements. The splitter does not silently evaluate SET
expressions, restore session state after transaction boundaries, or guess a
server/global default. Ordinary settings unrelated to quoting are retained.
The callbacks used for these checks are protected tokenizer extensions;
database-specific validation stays in the owning dialect modules.

This is a deliberate compatibility change for previously unchecked session-mode
statements: they now fail rather than returning potentially wrong boundaries.
Existing default-mode inputs and public configuration formats are unchanged;
only optional dialect splitter properties were added. The command/GUI preview
continues to use defaults and surfaces these errors. It does not expose advanced
mode controls; callers needing those policies use the dialect splitter API.
Effects hidden inside procedural bodies, dynamically executed SQL or server-side
routines cannot be inferred by an offline lexical splitter. Callers must ensure
that actual session quoting remains fixed for the entire submitted segment.

## Session policy verification (2026-10-25)

Final aggregate: 129 selected tests across 17 modules, with zero failures,
errors or skips in the isolated JUnit reports. Java 21 and the repository Gradle
Wrapper were used. The focused run first passed core tokenizer utilities plus
MySqlSqlSplitterSessionPolicyTest and PostgresSqlSplitterSessionPolicyTest.

The broad run used the same scopes as the additional-dialect verification above
(`*SqlSplitter*Test` also includes the new session-policy tests). Core through
PostgreSQL scopes completed successfully, including MySQL/MariaDB initial modes,
reuse, setting-change rejection, executable comments and false-positive cases.
DB2's full test-source compilation then stopped making progress; the identified
Java 21 daemon thread repeatedly reported javac Types.lub/merge/intersect type
inference frames. Only this verification client's wrapper and daemon were stopped.

The remaining successful invocation covered:

- DB2, Firebird, SQLite, Spanner and Vertica splitter and resolver tests.
- MigrationSqlInspectionTest, including error propagation for session settings.
- SqlBoundaryPreviewTest and SqlSplitPreviewPanelTest.

A generated verification init script limited DB2 compileTestJava sources to
`**/util/*SqlSplitter*Test.java` and `**/resolver/*Test.java`, matching the tests
selected for execution. No DB2 product or test source was changed for this
workaround. The remaining invocation completed BUILD SUCCESSFUL. Full DB2 test
compilation and full module suites are not claimed as verified. An initial init
script attempt used an early task lookup and failed before tests; deferred task
configuration corrected it.

Verification output and the init script remain under the ignored
`build/splitter-verification/` directory; no repository build settings, dependency
versions, generated migration artifacts or external databases were changed.

## DB2 compilation follow-up (2026-10-25)

The earlier compilation stall did not recur. Standalone Java 21 compilation of
all DB2 test sources with annotation processing disabled completed successfully.
Small probes for heterogeneous Function/Procedure and View/Mview lists also
compiled successfully, so no speculative type-inference source fix was made.
The original stall's cause remains undetermined.

Using a generated init script without the DB2 test-source filter, the repository
Gradle Wrapper completed full `:sqlapp-core-db2:compileTestJava` and the selected
splitter/resolver tests (7 tests). Then the full DB2 module suite passed:
257 tests in 70 classes, zero failures, errors or skips. Both runs used Java 21,
isolated build output and project cache, and `--offline`. No external database
was accessed and no public API, product source or repository build setting was
changed in this follow-up.

Commands (with JAVA_HOME set to the installed Java 21 runtime):

```powershell
.\gradlew.bat :sqlapp-core-db2:test --tests '*SqlSplitter*Test' --tests '*resolver.*Test' --init-script build/splitter-verification/full-db2.gradle --project-cache-dir build/splitter-verification/cache --offline --console=plain
.\gradlew.bat :sqlapp-core-db2:test --init-script build/splitter-verification/full-db2.gradle --project-cache-dir build/splitter-verification/cache --offline --console=plain
```

This supersedes the earlier full-DB2-compilation verification limitation. Full
suites for the other affected modules and external database tests remain
unexecuted. The full-suite log is in the ignored
`build/splitter-verification/db2-full-suite.log`.
