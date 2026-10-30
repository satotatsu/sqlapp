# Informix enhancement status

Assessment date: 2026-10-30.

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
