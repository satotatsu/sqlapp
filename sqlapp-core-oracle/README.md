# Oracle dialect

## Scheduler job inventory

Oracle 10g and later expose accessible Scheduler jobs through the normal
`schemaReader.getEventReader()` and full Schema-read entry points. The reader
uses one filtered `ALL_SCHEDULER_JOBS`/`ALL_SCHEDULER_JOB_ARGS` join for the requested owners and job names;
it does not query once per job. Referenced Programs and Schedules are fetched by
up to two additional set-based queries, for a maximum of three inventory queries regardless
of the number of jobs/arguments. Unreferenced dependency queries are skipped.
The pre-10g reader and resolver boundaries remain
unchanged. This covers Scheduler jobs, not legacy DBMS_JOB jobs.

On 23ai/26ai, full reads additionally capture native Scheduler scripts in one
batched `DBMS_METADATA.GET_DDL('PROCOBJ', name, owner)` SELECT, for a maximum of
four reader queries. Ordinary reads and pre-23ai readers retain inventory-only
behavior. The native function is called per selected object within that SELECT;
this does not claim constant database-internal work. Shared Program/Schedule
identities are selected once, and returned rows are matched by kind, owner and name.

Each job is an Event with its owner/name, inline action, comments and
enabled state. Additional inventory is stored in `oracle.scheduler.*` specifics:
`kind=JOB`, `JOB_CREATOR`, `JOB_TYPE`, Program/Schedule owner and name, `NUMBER_OF_ARGUMENTS`,
`REPEAT_INTERVAL`, class, auto-drop/restart settings, state, selected limits,
destination and NLS environment. `START_DATE_TEXT`, `END_DATE_TEXT`,
`LAST_START_DATE_TEXT` and `NEXT_RUN_DATE_TEXT` preserve fractional seconds,
timezone region and UTC offset. The reader does not coerce Oracle calendar
expressions into MySQL interval fields or timezone-bearing values into the
shared timezone-free Timestamp properties. Named Program jobs may have no inline
action; it is not invented from the Program reference. The shared definer field
is left unset: the creator can differ from the owner, and external/credentialed
jobs have execution identities that this baseline inventory does not resolve.

This is read-only inventory, not a complete job deployment representation.
Modern full reads preserve the job base script in `oracle.scheduler.definition`;
`Event.definition` is empty because the native script can omit arguments and
cannot serve as a complete executable definition. Accessible referenced Program/Schedule scripts are
stored in `oracle.scheduler.program.definition` and
`oracle.scheduler.schedule.definition`. `definitionStatus=AVAILABLE` marks a
captured script, and `definitionContext=SOURCE_OWNER` records its original-owner
execution context. Oracle may emit unqualified names, enable calls, commits or
session operations; the reader does not rewrite them. The scripts are reviewable
source metadata, not a portable or automatically executable migration bundle.

All targets are fetched and validated before publishing scripts. ORA-01031 or
ORA-31603 discards the entire partial script batch, logs the failure and marks
`definitionStatus=INACCESSIBLE` while retaining inventory. Other failures,
duplicate rows and missing/blank definitions fail explicitly. Empty full reads
skip the DDL query. No DBMS_METADATA session transforms are changed.

Chains, credentials, grants and unreferenced dependency objects are not captured
as a deployment closure. CREATE/ALTER/DROP SQL generation
fails explicitly; a complete Schema CREATE containing Scheduler Events also
fails instead of silently omitting them. A job body or caller-supplied definition
does not bypass this restriction. Unchanged ALTER diffs produce no SQL. An
explicit DBMS_SCHEDULER migration must account for dependencies, arguments and
enable state before deployment. Reading metadata never enables or runs jobs.

Argument inventory uses `oracle.scheduler.argumentCount` for the number of
observed catalog rows, distinct from the declared `NUMBER_OF_ARGUMENTS`.
`oracle.scheduler.argument.N.*` uses the catalog's one-based argument position:
`name`, `type`, `value`, `outArgument` and `hasAnydataValue`. Missing optional fields
are omitted. Text values retain whitespace, line breaks and quotes through XML.
The ANYDATA payload itself is not serialized or converted to text; a present
payload is marked `hasAnydataValue=TRUE`, so missing text is not mistaken for a
recreatable null value. Text and ANYDATA can both be present, including values set
through SET_JOB_ARGUMENT_VALUE; the flag reports payload presence, not the setter
or a mutually exclusive argument kind. The catalog snapshot does not resolve Program defaults or
distinguish every unset/SQL-null case. Duplicate, invalid or nonpositive positions
fail explicitly. Same-named jobs in different owner schemas remain separate.

Referenced Program and named Schedule baseline snapshots are retained separately
under `oracle.scheduler.program.*` and `oracle.scheduler.schedule.*`.
`status=AVAILABLE` means a matching catalog object was read, not that deployment
metadata is complete. `UNAVAILABLE` records a referenced object absent from the
accessible catalog (also applicable to window/window-group references);
`UNRESOLVED` records a missing reference owner without guessing the job's schema.
Owner and name are matched together. Catalog query failures still propagate.

Program snapshots contain `PROGRAM_TYPE`, `PROGRAM_ACTION`, `NUMBER_OF_ARGUMENTS`,
`ENABLED`, `COMMENTS`, and `argumentCount` for the observed definitions. Each
one-based `program.argument.N.*` preserves `name`, `type`, `defaultValue`,
`metadataAttribute`, `outArgument` and `hasDefaultAnydataValue`. ANYDATA default
payloads are not serialized. Schedule snapshots retain `START_DATE_TEXT`,
`END_DATE_TEXT`, `REPEAT_INTERVAL` and `COMMENTS`, including timezone information.
These snapshots are attached to the referencing Event, not separate shared-model
Program/Schedule objects, and unreferenced objects are not inventoried.

Program defaults are not merged into job argument values. Both catalog snapshots
are preserved; a job value alone does not prove that a user explicitly overrode
a default. Metadata arguments are not evaluated. These are separate catalog
SELECTs, with no cross-query locking or transaction-snapshot guarantee. Only the
10g common columns are read; newer event/file-watcher/chain attributes and
structured dependency DDL editing remain outside this inventory. Native scripts
captured during modern full reads are supplemental to these baseline snapshots;
newer attributes that Oracle includes in those scripts are not individually modeled.

Dependency snapshot verification on 2026-11-04: eleven focused Scheduler tests,
559 Oracle module tests and 142 selected Command generator tests passed with
zero failures/errors/skips. The new
`disabledSchedulerDependenciesAndDefaultsSurviveXml` case compiled. It creates
one Program and one Schedule shared by two disabled jobs, defines a Program
default and sets one job's argument value, then checks both snapshots through XML.
It cleans up jobs before their dependencies and preserves cleanup errors. This
additional case was separately authorized because the earlier four-case matrix
covered only inline jobs and procedure argument bindings.

Dependency live qualification on 2026-11-04: the additional case passed on both
`gvenzl/oracle-free:23-slim-faststart` and
`gvenzl/oracle-free:23.26.3-slim-faststart`, one case per image and zero
failures/errors/skips. Each used one disabled Program, one Schedule and two
disabled jobs. Program defaults retained line breaks, quotes and whitespace in
XML while a job-specific value remained separate; the shared Schedule calendar
and timezone offset were retained. No Program or job was enabled or run.
Cleanup completed for all created objects, and each run's Oracle/Ryuk containers
were checked absent. Neither image declares anonymous volumes; no volumes
remained for the recorded 26ai session. Existing databases and containers were
not used. No production code changed during this qualification, and the earlier
559/142 unit scopes were not rerun just for the live check.

```powershell
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests '*OracleMetadataReaderTest.disabledSchedulerDependenciesAndDefaultsSurviveXml' -PtestcontainersReuse=false -PoracleTestImage=gvenzl/oracle-free:23-slim-faststart -PoracleExpectedDialect=23ai
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests '*OracleMetadataReaderTest.disabledSchedulerDependenciesAndDefaultsSurviveXml' -PtestcontainersReuse=false -PoracleTestImage=gvenzl/oracle-free:23.26.3-slim-faststart -PoracleExpectedDialect=26ai
```

Existing APIs and XML formats are retained; newly read Schema XML gains Events
and their specifics. This can expose jobs that older sqlapp exports omitted.
Catalog access errors and invalid enabled/identity fields propagate explicitly.
No module dependency was added. The disabled-job integration case
`OracleMetadataReaderTest.disabledSchedulerJobsRetainCalendarAndTimezoneThroughXml`
and argument case `disabledSchedulerJobArgumentsSurviveXml` are separate from
the external-table matrix and were explicitly authorized and run on both images.
Its setup grants CREATE JOB only within its disposable container, creates a
disabled future job and drops that job in cleanup; it never runs the job. The
argument case additionally grants CREATE PROCEDURE, creates an unused procedure
and an inline STORED_PROCEDURE job with two string-valued argument bindings, then
drops both in cleanup. Neither test enables or executes its job or procedure.

Reference: [Oracle 10g ALL_SCHEDULER_JOBS](https://docs.oracle.com/cd/B13789_01/server.101/b10755/statviews_1141.htm).
Argument reference: [Oracle 10g ALL_SCHEDULER_JOB_ARGS](https://docs.oracle.com/cd/B13789_01/server.101/b10755/statviews_1137.htm).

Scheduler inventory verification on 2026-11-04: five focused reader/SQL tests,
553 Oracle module tests and 142 selected Command generator tests passed with
zero failures/errors/skips. The disabled-job Docker case compiled successfully
but was not executed; no database was accessed in this follow-up. No tracked
generated output, dependency version or shared model API was changed.

Scheduler argument/live follow-up on 2026-11-04 supersedes the pending database
qualification above. The one-query reader now preserves argument positions,
names, types, text values, reserved OUT_ARGUMENT text and ANYDATA presence. Seven
focused tests passed, including whitespace/newline/quote retention through XML,
same-name jobs in different schemas and rejection of duplicate/invalid positions.

Both disabled-job cases passed on each of `gvenzl/oracle-free:23-slim-faststart`
and `gvenzl/oracle-free:23.26.3-slim-faststart`: four engine/case combinations,
zero final failures/errors/skips. Calendar/timezone fields and zero-argument
inventory were read back, and stored-procedure job values retained line breaks,
quotes and trailing spaces after XML. ANYDATA presence was compared with the
actual catalog. The first Oracle23 run had one failed assertion because the
test assumed a string binding implied no ANYDATA payload. Oracle populated both;
the test was corrected to compare the catalog and a coexistence unit regression
was added. The production presence mapping already reported the catalog correctly.

All jobs remained disabled and no test action or procedure was executed. Job and
procedure cleanup succeeded, and all Oracle/Ryuk containers from the initial,
retry and 26ai runs were checked absent. The Oracle23 containers had no anonymous
volume mounts; the fixed 26ai image declares no volumes and its dedicated
container was also removed. Only disposable Testcontainers credentials were used;
existing containers/data, external/production databases and local configuration
were untouched. Final Oracle unit verification passed 555 tests; the selected
Command generator scope retained its 142 passing tests (UP-TO-DATE on the final
check). Public APIs, resolver boundaries and dependencies were unchanged.

```powershell
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests '*OracleMetadataReaderTest.disabledSchedulerJobsRetainCalendarAndTimezoneThroughXml' --tests '*OracleMetadataReaderTest.disabledSchedulerJobArgumentsSurviveXml' -PtestcontainersReuse=false -PoracleTestImage=gvenzl/oracle-free:23-slim-faststart -PoracleExpectedDialect=23ai
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests '*OracleMetadataReaderTest.disabledSchedulerJobsRetainCalendarAndTimezoneThroughXml' --tests '*OracleMetadataReaderTest.disabledSchedulerJobArgumentsSurviveXml' -PtestcontainersReuse=false -PoracleTestImage=gvenzl/oracle-free:23.26.3-slim-faststart -PoracleExpectedDialect=26ai
```

```powershell
.\gradlew.bat :sqlapp-core-oracle:test --tests '*Oracle10gEventReaderTest'
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-core-dialect-test:compileDockerTestJava :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*'
```

## Disabled job restoration

`OracleDisabledSchedulerJobRestorer.restore(connection, exportedJob)` restores
one **absent, disabled, argument-free inline job** from a current full-read Event
or its XML round-trip. This initial subset supports `PLSQL_BLOCK` and
`STORED_PROCEDURE`, on Oracle 23ai/26ai engines reporting database major version
23. The generic Event CREATE/ALTER/DROP factories remain blocked.

```java
// exportedJob is the Event read in full from the source, or imported from its XML.
// connection is a dedicated auto-commit connection to an absent target job.
var result = OracleDisabledSchedulerJobRestorer.restore(connection, exportedJob);
System.out.println(result.completedOperations());
```

Connect as the original owner, with session user, current user and current schema
all equal to that owner. No owner mapping, grants, existing-object replacement,
DROP, ENABLE or RUN_JOB operations are performed. Use an exclusive maintenance
window with no concurrent target changes. The service checks target absence and
verifies disabled state after creation; it is not a transaction or concurrency
lock. Use a dedicated connection with auto-commit already enabled so caller
transactions are not silently committed.

The original script is never executed. A strict parser accepts one native
CREATE_JOB with explicit `enabled=>FALSE`, the captured NLS_ENV setter and the
native COMMIT/END wrapper. It rejects additional calls, dynamic expressions,
comments outside literals, unsupported attributes and ambiguous syntax before
even inspecting the target connection. Only newly constructed, SYS-qualified
CREATE_JOB and NLS_ENV calls run; string values (including action bodies and date
text/format/NLS arguments) are bound parameters. The original COMMIT is omitted.
The job action remains stored data and is never run.

Source inventory must have zero declared and observed arguments, no argument
payload fields or Program/Schedule references, no destination, default priority
3, restartable FALSE and no max-runs/max-failures limits. Unsupported native
attribute setters also fail. Full reads now add
`oracle.scheduler.definitionFingerprint`, covering the captured Event, native
scripts and supplemental metadata. XML round-trips retain it; changed metadata
or old exports without this fingerprint require a fresh full read. The
fingerprint detects edits, not authenticity or malicious tampering.

Scheduler operations are not atomic. On failure after writes start,
`RestorationException.getPartialResult()` identifies completed operations and
`getFailedOperation()` identifies the attempted operation. A failed call may
already have taken effect. Inspect the target before retrying; no automatic
cleanup or rollback is attempted. Program/Schedule restoration, argument/ANYDATA
restoration, non-default execution attributes and enabling jobs remain unsupported.

Verification (2026-11-04): eight restorer tests cover bound values, quoted names,
Unicode/action lookalikes, unsupported syntax, argument/ANYDATA rejection,
context/version/transaction/target guards, partial failure, disabled-state failure
and fingerprint integrity. The final focused run also checked Schema XML import.
The full Oracle scope passed 579 tests and the Command generator scope passed
142, all with zero failures/errors/skips. The reader/restorer focused scope passed
14 tests before the final Schema-XML assertion was added to an existing test.

The previously authorized disabled inline calendar fixture was dropped and
recreated from its exported Event XML on both disposable Oracle 23 and fixed
26ai images. Both final cases passed, comparing body, calendar, timezone-bearing
dates, class, auto-drop, NLS environment, argument counts and disabled state.
An initial Oracle 23 smoke run also passed before fingerprint enforcement was
added. No job was enabled or run; only the dedicated test job was dropped and
recreated. Test objects were cleaned up, and the runs' dedicated Oracle/Ryuk
containers were checked absent. No task exclusions were used.

Commands used for the final scopes:

```powershell
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*'
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests '*OracleMetadataReaderTest.disabledSchedulerJobsRetainCalendarAndTimezoneThroughXml' -PtestcontainersReuse=false -PoracleTestImage=gvenzl/oracle-free:23-slim-faststart -PoracleExpectedDialect=23ai
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests '*OracleMetadataReaderTest.disabledSchedulerJobsRetainCalendarAndTimezoneThroughXml' -PtestcontainersReuse=false -PoracleTestImage=gvenzl/oracle-free:23.26.3-slim-faststart -PoracleExpectedDialect=26ai
```

Public changes are additive Oracle utility APIs and one full-read fingerprint
specific. No shared Schema API, resolver boundary, dependency or pre-23ai metadata
query changed. Live restoration qualification covers the inline PLSQL_BLOCK
calendar fixture; STORED_PROCEDURE syntax is accepted but not yet live-qualified
by a zero-argument procedure fixture. Broader restoration remains unsupported.

### Native Scheduler script verification

#### Collecting scripts for review

`OracleSchedulerScriptReview.collect(events)` collects native scripts from full
Oracle 23ai/26ai metadata reads into an immutable list, ordered as Program,
Schedule, then Job. Shared dependencies with identical owner/name and script
are included once. Owners and names are compared exactly, including case.

**PROCOBJ scripts are base scripts, not complete recreation DDL.** A live Oracle
23 follow-up found that a job with two explicit argument values exported neither
value, and a Program with one argument/default exported neither the argument
declaration nor its default. These are retained in the structured specifics, not
in the native script. Job scripts are now stored as
`oracle.scheduler.definition`, alongside Program/Schedule script specifics;
`Event.definition` remains empty. `definitionStatus=AVAILABLE` means a base script
was retrieved, not that the object is recreatable from that script alone.

Follow-up qualification (2026-11-04): Oracle module tests passed 571 cases;
the focused reader/review scope passed 12. The three authorized disabled-Scheduler
cases passed on both Oracle 23 and 26ai (six cases total), including XML retention
of base scripts, source identities and supplemental model snapshots. No task
exclusions were needed. A separate Oracle 23 run of the same three fixtures was
used to inspect native output. None of these runs replayed a script or enabled
or ran a job. Their dedicated Oracle/Ryuk containers were checked absent.

The Command generator scope initially had one failure out of 142:
`NoClassDefFoundError: com/sqlapp/util/iterator/IntegerIterator`. The cause was
not established; no shared source was changed for it. An independent retry
passed all 142 cases. The ordinary Scheduler SQL-generation guard remains in
place: reconstructing argument values/defaults and validating disabled-state
execution are still required before implementing automatic restoration.

```java
var review = OracleSchedulerScriptReview.collect(schema.getEvents());
for (var script : review.scripts()) {
    // Display/store each identity and original definition for manual review.
    System.out.println(script.kind() + " " + script.owner() + "." + script.name());
    System.out.println(script.definition());
}
for (var job : review.jobs()) {
    // Keep the full source model with the scripts: arguments/defaults are here.
    System.out.println(job.xml());
}
```

The collector requires disabled jobs and disabled referenced Programs, available
dependency snapshots, native scripts and their source-owner context. It fails
on duplicate jobs or conflicting shared scripts, rather than choosing a version.
Captured `definitionOwner` and `definitionName` specifics accompany each script;
changing a modeled identity without refreshing its script is rejected. Older XML
exports without these identity fields must be refreshed before collection.
Exports from the earlier canonical `Event.definition` representation also require
a fresh full read; the collector does not mistake that older script for a complete
definition. `jobs()` contains immutable XML snapshots of the selected Events so
argument/default metadata is available even after the caller changes its models.

This is a **manual review bundle, not an executable restoration plan**. It does
not validate PL/SQL contents, establish script authenticity, detect arbitrary
edits to other modeled attributes, inspect target objects, guarantee job state
after replay, remap owners or restore additional dependencies such as procedures,
job classes and credentials. Native scripts can enable objects or change session
state even when catalog inventory says disabled. Generic Event CREATE/ALTER/DROP
remain blocked. No database connection or execution is performed by this API.

Verification (2026-11-04): the focused review/reader scope passed 11 tests;
`:sqlapp-core-oracle:test` passed 570 tests; `:sqlapp-command:test --tests
'com.sqlapp.data.db.command.generator.*'` passed 142 tests. All had zero
failures/errors/skips. No live database tests were run for this collection-only
change. Public API additions are confined to the Oracle module; existing APIs and
pre-23ai reader selection are unchanged.

Refreshing an existing Event clears previously captured job, Program and Schedule
scripts and their definition context/status before requesting the new batch. A
denied or failed refresh cannot leave an earlier executable script attached to
the refreshed metadata. Dependencies that are no longer available also lose their
previous scripts. Inventory fields remain intact. This does not authorize or
implement automatic script replay.

On 2026-11-04, four new native-script tests and the eleven baseline Scheduler
tests passed. The Oracle module scope passed 563 tests and the selected Command
generator scope passed 142 tests with zero failures/errors/skips; Docker-test
compilation also passed. This historical check originally used the shared
definition line-list representation; the follow-up above moves incomplete base
scripts into specifics. Native enable calls, quotes and Unicode remain
in the captured content, and CREATE generation still rejects replay.

The three previously authorized disabled-Scheduler cases were rerun on each of
`gvenzl/oracle-free:23-slim-faststart` and
`gvenzl/oracle-free:23.26.3-slim-faststart`: six engine/case combinations passed
with zero final failures/errors/skips. Full reads captured job scripts and shared
Program/Schedule scripts, preserved them through XML, and retained disabled
state. Neither the captured scripts nor the jobs were executed. Created objects
were cleaned up, each run's Oracle/Ryuk containers were checked absent, and
neither image declares anonymous volumes. Existing containers/data were not used.

The first 26ai invocation did not reach tests: unrelated in-progress DB2 source
`Db2HostProgrammableReaders.RoutineGrants` failed compilation at a no-argument
`getSchemaName()` call. DB2 sources were left untouched. The 26ai retry reused
the existing DB2 jar and Docker test classes that had just passed on Oracle23,
excluding `:sqlapp-core-db2:compileJava`, `:sqlapp-core-db2:jar` and
`:sqlapp-core-dialect-test:compileDockerTestJava`. All three Oracle26ai cases then
passed. This qualifies the Oracle implementation, not the current full workspace
build or the unrelated DB2 changes. Public entry points, resolver boundaries,
dependencies and shared Schema APIs were unchanged.

Use the three `OracleMetadataReaderTest.disabledScheduler*` cases for this
matrix. The 26ai retry used the following additional task exclusions:

```powershell
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests '*OracleMetadataReaderTest.disabledScheduler*' -PtestcontainersReuse=false -PoracleTestImage=gvenzl/oracle-free:23.26.3-slim-faststart -PoracleExpectedDialect=26ai -x :sqlapp-core-db2:compileJava -x :sqlapp-core-db2:jar -x :sqlapp-core-dialect-test:compileDockerTestJava
```

Pre-23ai full-read compatibility remains inventory-only. Live qualification used
disabled inline and named Program/Schedule fixtures only; enabled-source scripts,
window/chain/credential deployment, owner remapping and actual script replay or
enable-state restoration remain unqualified. The SOURCE_OWNER context marker is
a deployment prerequisite, not a guarantee that scripts can be run in any schema.

## External tables

Oracle 10g and later table readers identify external tables and include them in
the existing batched `DBMS_METADATA.GET_DDL('TABLE', ...)` capture used for IOT and
composite tables. Use the normal full table/schema read, Schema XML export/import,
and CREATE SQL registry entry points. The saved complete table DDL preserves
`ORGANIZATION EXTERNAL`, all locations, driver access parameters and reject limits.
The existing `OracleTableDefinition` representation and stale-model checks apply;
the captured definition is authoritative and structured components are supplemental.

The external-table reader still uses one catalog query for the requested object
set. All returned location rows are retained in `ExternalTable.specifics`:

- `oracle.external.locationCount`: number of returned locations.
- `oracle.external.location.N.file`: file/location text, with zero-based N.
- `oracle.external.location.N.directory`: directory name, when available.
- `oracle.external.location.N.directoryOwner`: directory owner, when available.
- `oracle.external.location.N.partition`: partition name for partition-level locations.
- `oracle.external.location.N.subpartition`: subpartition name for subpartition-level locations.

On Oracle 12.2+, the same query unions ordinary, partition and subpartition location
views; each entry keeps its owning partition/subpartition identity. Exact server
version information selects the 12.2 query boundary. If the version is unavailable,
the 12c dialect retains its legacy query because it also represents 12.1; an 18c+
dialect selects the newer query. Older servers never reference the newer views.
Rows are sorted by partition/subpartition, directory owner, directory name and location for stable output;
duplicates are retained. Existing `location` and `directoryName` properties describe
the first row, preserving the single-location API and existing XML attributes.
These fields are inventory metadata; editing them does not rewrite the captured
table CREATE definition. No shared model API, resolver, dependency or configuration
format changed. Existing XML remains readable; newly read XML gains specifics.

If complete DDL cannot be obtained because of metadata permissions, catalog
metadata remains available, but CREATE of an identified external table fails
explicitly instead of creating an ordinary heap table. A legacy Table attached to
a Schema with matching external-table metadata is also rejected without complete
DDL. Standalone legacy Table XML without external identification cannot be inferred.
Pre-10g table queries and definition-capture behavior are unchanged.

External-table Table diffs require an explicit external-table migration plan for
any change, including partition maintenance, rename, columns and indexes. The
generic heap/IOT planners are not used for these changes. Identification uses
either side's external marker or matching legacy Schema external-table metadata,
so clearing a target marker does not turn an external-table change into a heap
migration. Unchanged models produce no SQL; any captured definitions are still
checked for staleness. This restriction also applies to catalog-only models after
metadata permission denial. Native external-table ALTER planning is not added.

ALTER regression verification on 2026-11-04: the new tests first reproduced three
failures where external changes were accepted. After the fix, all five new ALTER
tests, 548 Oracle module tests and 142 selected Command generator tests passed
with zero failures/errors/skips. Cases include partition DROP/MOVE/SPLIT, marker
removal, columns, legacy Schema identification, unchanged models, stale definitions
and a captured partitioned table after XML. No database test was run in this ALTER
follow-up. Changes are limited to Oracle; public APIs, resolver boundaries,
configuration formats and dependencies are unchanged.

Directory objects, access privileges and external data files must already exist on
the destination. This change does not copy files, grant access or execute external
drivers. Complete DDL remains the recreation representation. Ordinary,
partitioned and subpartitioned external-table recreation was qualified on the
Oracle23 and fixed 26ai Docker images below. Older engines, actual external-file
reads and other access drivers still need separate qualification.

Validation uses Java 25 and the repository Gradle Wrapper:

```powershell
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleExternalTableRecreationTest'
.\gradlew.bat :sqlapp-core-oracle:test
.\gradlew.bat :sqlapp-core-dialect-test:compileDockerTestJava
```

The new `OracleMetadataReaderTest.externalLocationsSurviveMetadataXmlAndRecreation`
Docker case is compiled separately and requires explicit database-test authorization
before execution. Partitioned and subpartitioned equivalents are also included.
Unit tests cover one-query multi-location metadata including
duplicates and quoted file names, Schema XML, batched definitions, stale definitions,
permission denial, missing definitions, legacy input and version boundaries.

Verification on 2026-11-03: Oracle module 541 tests and selected Command generator
142 tests passed with zero failures/errors/skips. Docker-test compilation passed;
the new database test was not executed and no database was accessed. No tracked
generated output or dependency versions were intentionally changed.

Partition-location follow-up on 2026-11-03: 543 Oracle module tests and 142 selected Command
generator tests passed. The ordinary/partition/subpartition Docker cases compiled;
none was executed. The new query and metadata mapping are unit-tested, not
qualified against a live 12.2+ catalog in this follow-up.

Live qualification on 2026-11-04 supersedes that outstanding Docker check. The
three `*LocationsSurviveMetadataXmlAndRecreation` cases passed on each of
`gvenzl/oracle-free:23-slim-faststart` and
`gvenzl/oracle-free:23.26.3-slim-faststart`: six engine/case combinations, zero
failures/errors/skips. The fixed image reported Oracle AI Database 26ai Free
23.26.3.0.0. Each case read metadata, retained both locations and partition names
through XML, dropped its generated test table, recreated it through the ordinary
SQL registry and compared the location inventory again. Quoted file names were
included. CREATE SQL is generated and the table identity checked before DROP.

Only the explicitly authorized disposable containers and their test credentials
were used. Their test tables were cleaned up and both Testcontainers sessions
were checked absent afterward. Neither Oracle container had an anonymous volume
mount; no session-labelled volumes remained. Existing containers and data were
not used. Module unit tests were not rerun in this live-only follow-up; the
Docker-test compilation and the six database cases were executed.

```powershell
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests '*OracleMetadataReaderTest.*LocationsSurviveMetadataXmlAndRecreation' -PtestcontainersReuse=false -PoracleTestImage=gvenzl/oracle-free:23-slim-faststart -PoracleExpectedDialect=23ai
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests '*OracleMetadataReaderTest.*LocationsSurviveMetadataXmlAndRecreation' -PtestcontainersReuse=false -PoracleTestImage=gvenzl/oracle-free:23.26.3-slim-faststart -PoracleExpectedDialect=26ai
```

References: [external tables](https://docs.oracle.com/en/database/oracle/oracle-database/21/admin/managing-tables.html),
[DBMS_METADATA](https://docs.oracle.com/en/database/oracle/oracle-database/19/arpls/DBMS_METADATA.html).
Partition-location references: [ALL_XTERNAL_LOC_PARTITIONS](https://docs.oracle.com/en/database/oracle/oracle-database/12.2/refrn/ALL_XTERNAL_LOC_PARTITIONS.html),
[ALL_XTERNAL_LOC_SUBPARTITIONS](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/ALL_XTERNAL_LOC_SUBPARTITIONS.html).
