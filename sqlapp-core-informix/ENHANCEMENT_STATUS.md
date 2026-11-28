# Informix enhancement status

Assessment date: 2026-10-30.

Follow-up implementation: 2026-11-20. Native basic ALTER, explicit rejection of
unsafe storage/constraint-dependent changes, SPL script splitting, shared MERGE
empty-update handling, temporary-table APIs, single sequence-value SQL and
set-based authorization metadata readers have been added. See the README for
the supported operations, tests and compatibility boundary. This follow-up used
unit/model tests only and does not extend the live-server compatibility claim.

The continuation adds dbspace/server-setting readers, collection-based bulk
sequence allocation, verified JDBC PUT-mode selection and the staged atomic
replacement provider. These paths have unit/model coverage and an added,
compiled-only Docker integration test. They do not yet have new live-server
evidence. Role create/drop, role membership grant/revoke (including default role
and grant option), and named routine EXECUTE grant/revoke factories are now
implemented with unit/model coverage. Routine grants distinguish executable
SPECIFIC names from synthetic catalog-type-ID identities. An empty synthetic
signature is supported. Catalog CREATE emits the grants after routine creation.
User connected-database authorization grant/revoke and ALTER now preserve the
C/R/D/U privilege distinction, revoke elevated levels before CONNECT, and reject
unsupported account attributes. Membership ALTER supports default-role changes
and grant-option upgrades, while explicitly rejecting grant-chain revocation.
Table grants/revokes and column grants now preserve grantor and grant-option
semantics. Object/column privilege readers use one set-based query each and
strictly decode the vendor privilege patterns. Column-specific revoke is rejected
because Informix only revokes these privileges across all columns; a complete
grant-set and downstream dependency plan remains necessary.
Complete dependency-aware ALTER/security DDL and external-file/HPL
loading remain outside the implemented scope. Bulk upsert and snapshot providers
continue to use their existing execution paths.

Catalog-level ALTER now provides an initial constraint-dependency plan for column
definition changes with stable table/column identities. Given complete connected-
database snapshots, it drops foreign keys before keys/checks, modifies all affected
tables, restores keys/checks, then foreign keys. Cross-owner and self-references,
composite keys and named NOT NULL have unit/model coverage. Standalone table
safety checks remain. Standalone indexes are now retained through MODIFY, and
modeled enabled named backing indexes are ensured once after column changes and
before key restoration using CREATE INDEX IF NOT EXISTS (known 11.70+ only).
Owner/storage/options reuse the native index factory. Unsupported backing-index
states, mixed structural changes and full dependency migration remain rejected;
see README prerequisites. Index survival/reuse in this plan lacks live evidence.
Independent-index additions/removals/renames and definition changes can now be
combined with column MODIFY, or planned alone. Changed indexes are dropped after
constraint removal and recreated after all column changes, before restoring keys.
Native DDL retains owner, storage and supported index modes. On Informix 11.70
and later, changes to enabled named backing indexes use conditional DROP after
constraint removal and unconditional CREATE before key restoration. The same
path supports renaming an index and updating the key's reference when there
is no competing index on the same columns. INCLUDE payload columns remain unsupported.
Catalog ALTER also supports PRIMARY KEY and UNIQUE additions, removals and
replacement, plus foreign-key mode changes. Retained foreign keys require
an enabled matching key when their referenced keys change.

## Completion scope

The normal-use enhancement can be considered complete for the paths covered by
the Informix unit tests and InformixMetadataDockerTest: metadata retrieval,
Schema/Catalog XML, native CREATE operations, and behavior after recreation.
This is a completion boundary, not a claim of exhaustive coverage of every
Informix feature or configuration.

Coverage includes ordinary and typed tables, column declarations/defaults,
ROW/DISTINCT and nested collection types, indexes and backing indexes,
constraints/modes, supported fragmentation policies, views, routines, triggers,
sequences and synonyms. Supported database-backed cases are exercised on the
12.10.FC12W1DE, 14.10.FC9W1DE and 15.0.1.0.3 container images. The resolver still
retains its older-version behavior; no new version boundary was introduced.

The final audit adds inherited-only ROW subtypes, multi-level inherited NOT NULL
enforcement, and duplicate rejection by a recreated functional UNIQUE index.
ROW subtype syntax omits the field list when no new fields are introduced;
an empty child field list is not the equivalent syntax.

## Remaining work and prerequisites

The follow-up verification runs the Informix module tests and compiles the
Docker suite. The suite includes overload-specific routine grants, partial
column revocation, constraint and index preservation, and lock mode and
next-extent readback after ALTER. These Docker cases still need execution
against an explicitly selected disposable server. The earlier live coverage
does not extend to the follow-up changes.

| Priority | Item | Classification | Next action |
| --- | --- | --- | --- |
| P1 | Informix 11.70 live compatibility | Unverified supported version | Obtain a disposable 11.70 environment and run the same metadata/recreation suite before claiming live compatibility. |
| P2 | Custom opaque value input/output | Library-specific validation | Supply the actual type, support functions and target libraries; test non-NULL values. Current opaque-column coverage checks type identity and neighboring values, not custom value conversion. |
| P2 | Library-specific UNIQUE backing access methods/operator classes | Specialized validation | Install a representative implementation in a disposable target. Name mapping has unit coverage; ordinary named backing-index reuse has live coverage. |
| P2 | Disabled interval policies with already generated fragments | Intentionally rejected data/storage-dependent case | Design and validate a data-loading and storage-recreation procedure. Re-emitting only initial ranges is insufficient, and reserved generated partition names cannot simply be recreated. |

P1 concerns the breadth of the compatibility claim. P2 items are outside the
normal-use completion boundary and do not require more mandatory configuration
for ordinary users.

## Safety checks are not all missing features

- Functional UNIQUE indexes are supported. A constraint catalog that exposes
  function keys is rejected because the constraint model cannot reliably
  recreate them as ordinary constrained columns. In the 15.0.1.0.3 audit,
  UNIQUE(ABS(id)) was rejected by the server parser. This is distinct from
  recreating a standalone functional UNIQUE index.
- Inherited-only ROW subtypes are now supported. An empty root ROW definition,
  missing member/type identities, cyclic anonymous types and unknown catalog
  flags still fail explicitly.
- Unresolved virtual-index data locations require an explicit target space;
  a descriptor dbspace is not substituted for the data location.
- Native libraries, DataBlade installation and automatic global dependency
  planning remain explicit prerequisites or responsibilities outside this dialect.

No automatic production changes or external library installation are required
to finish the normal-use scope. Recreate prerequisite objects in dependency
order and execute the complete operation lists returned by the factories.

## Evidence

- [Unit tests](src/test/java/com/sqlapp/data/db/dialect/informix)
- [Docker integration tests](../sqlapp-core-dialect-test/src/dockerTest/java/com/sqlapp/data/db/dialect/informix/metadata/InformixMetadataDockerTest.java)
- [Reader implementation](src/main/java/com/sqlapp/data/db/dialect/informix/metadata)
- [SQL factories](src/main/java/com/sqlapp/data/db/dialect/informix/sql)
- [Run commands, image selection and detailed limitations](README.md)
- [IBM CREATE ROW TYPE syntax](https://www.ibm.com/docs/en/informix-servers/12.10.0?topic=statements-create-row-type-statement)
- [HCL UNIQUE constraint syntax](https://informix.hcldoc.com/12.10/help/topic/com.ibm.sqls.doc/ids_sqs_0516.htm)
