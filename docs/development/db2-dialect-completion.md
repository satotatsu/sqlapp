# Db2 dialect completion gates

The target is comparable reliability and coverage for shared Schema features,
plus faithful recreation of Db2-specific objects. It does not mean implementing
PostgreSQL-only SQL constructs in Db2. Passing unit tests alone does not close
the live compatibility gate. See [implementation evidence](db2-dialect-parity.md).

The [2026-10-27 PostgreSQL comparison audit](db2-postgres-final-audit.md) now
separates ordinary shared-feature completion from vendor expansion. It reproduced
incorrect ordinary column ALTER output and silent empty table/routine changes;
the existing passing suites do not close those gaps. The mandatory remainder is
two implementation packages (P1/P2) and one closing verification package (P3).
The offline evidence task does not execute SQL or assert parity.

## Current implementation

Supported logical XML indexes retain pattern/type/value-mode metadata and native
recreation; database-managed physical XML indexes are excluded. Conflicting or
unsupported XML forms still require complete DDL.

The DB2 module now has bulk metadata and native recreation coverage for ordinary
tables/columns/constraints/indexes, views/MQTs, routines, triggers, sequences,
aliases, roles/grants, native types, variables, module headers/member inventories and condition/variable/type recreation, federation
snapshots and row/column access control. Coverage is versioned; a modern feature
does not imply support in every historical Db2 release. Complete executable DDL
is the supported fallback for forms whose components cannot be read reliably.

Native ALTER now covers modeled sequence configuration without implicit restart,
regular index compression without replacement, and comment changes on sequences,
indexes, views, triggers and aliases. Structural replacement remains explicit
where preserving dependencies, privileges or execution order is required.

Weak and named-row strong cursor types now round-trip globally and in supported
module dependency chains. Proven generated type-cast functions remain in the
inventory but are recreated by CREATE TYPE rather than duplicated. Routine DDL
read from the catalog now binds an otherwise implicit SPECIFIC identity so grants
continue to target the recreated routine. Specific method grants and future
function/procedure/method scopes now retain their distinct identities.

Global named ROW fields, arrays of named types and strong cursors now share a
dependency order across the Type and Domain collections. Schema and Catalog
creation use that same order, reject cycles/duplicate identities, and preserve
the source models. Global strong cursors may target published module ROW types;
modules precede the global type group. Catalog creation also orders schemas by
these modeled type prerequisites when an acyclic whole-schema order exists.
Type cycles and schemas requiring interleaved generation fail clearly. General
cross-schema dependencies and the reverse direction (module members requiring
global types) remain explicit work.

Published SQL function/procedure prototypes now retain their unimplemented state.
Module recreation emits proven independent declarations first, retains the
dependent member order, then installs
routine bodies with ADD while restoring each routine's schema/path. This supports
published SQL routine cycles without changing private routines into public ones.
Bodies and their original options remain intact; unsupported declaration forms
still require a complete parent definition.
Default/constant expressions also regain the parentheses omitted by native
catalog text, through the shared global/module variable renderer.
Literal-only defaults can precede implemented routine timestamps; defaults with
routine references are also supported when all later implemented public routines
have proven independent declarations (built-in signature types, no parameter
defaults or anchoring). Unproven declaration dependencies still require complete
parent DDL. Procedure
declarations retain result-set counts, NULL-input and COMMIT ON RETURN clauses.

Current verification: Java 25, 345 DB2 unit tests and 14 real database tests passed
with zero failures/errors/skips. Real targets were disposable DB2 Community
11.5.8.0 and 12.1.5.0 containers, seven tests each. Both owned containers were
removed after verification. Common and dialect-test builds now both use Java 25;
the earlier Java-version mismatch no longer applies.

## Remaining gates

| Gate | Remaining work | Completion evidence |
| --- | --- | --- |
| P1: ordinary table/column ALTER | Native DEFAULT, nullability, supported type changes, table/column comment changes and removals; explicit unsupported-field handling | Direct Table and nested Schema/Catalog cases; executable SQL and reread on both live targets; rows preserved and REORG requirements reported |
| P2: routine changes and no-op safety | FUNCTION/PROCEDURE comments and supported native changes; verified replacement or explicit rejection for body/identity changes; registry/state routing | Meaningful changes cannot silently return an empty plan; direct and nested paths agree; live identity/grants checks |
| P3: closing verification | Common dependency/FK recreation, generated DML, applicable existing bulk/tree cases, historical resolver/query-resource boundaries, and re-run P1/P2 fixtures | Previously passing tests plus new gap regressions pass; exact executed/unexecuted server scope reported; refreshed offline evidence has no unexplained B1/B2 silent outcomes |

These are three work packages, not a promise of three chat turns or a reliable
person-day estimate. P3 may expose further concrete fixes. The audit inventory
is complete enough to prioritize the reproduced blockers; it does not certify
every object/option on every server version.

PL/SQL completeness, structured/anchored types, interleaved/general dependency
graphs, exotic XML/physical indexes, complex federation, LBAC and instance
authorities remain in the [separate expansion backlog](db2-postgres-final-audit.md#separate-expansion-backlog).
They were previously mixed into the mandatory checklist. Explicit native-DDL
fallbacks and unsupported-case errors remain part of the documented contract.

## Validation boundary

Unit tests use synthetic catalog rows and Schema models. The user authorized real
database verification for this continuation. The selected Docker suites actually
executed against owned 11.5.8/12.1.5 instances; no external or production database
was accessed. Earlier sections in the implementation history that say
"compile-only" describe their historical validation state.

The ALTER live fixture uses an isolated schema, checks generated SQL contains no
implicit CREATE/DROP/RESTART, rereads configuration/comments, and checks sequence
progress after changing increment/cache/order. Index compression changes only
the catalog setting; physical compression still requires a separate REORG and
is not performed by the generated ALTER plan.
