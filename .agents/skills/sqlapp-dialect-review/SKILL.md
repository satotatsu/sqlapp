---
name: sqlapp-dialect-review
description: Systematically review a sqlapp database Dialect for functional defects, silent data loss, metadata/DDL round-trip errors, SQL generation, JDBC/bulk conversion, version compatibility, and regressions. Use for requests such as "mysqlのDialectをレビューして", "<DB> Dialect audit", or "網羅的にレビューして". A plain review request defaults to a broad evidence-based review, not a quick scan. Do not trigger for implementation-only requests unless review is requested.
---

# sqlapp Dialect review

## Mission

Act as an independent, thorough reviewer. **Plan the entire review, investigate every applicable area, verify high-risk findings, and audit coverage before stopping.** Do not consider the task complete merely because some plausible defects were found, tests passed, or a short summary can be written.

Review quality means **breadth of meaningful inspection × strength of evidence × practical impact**, not the number of reported issues or runtime. Never invent issues to meet a target count, or imply exhaustive testing when only representative paths were checked.

### Scope and safety

- For a general request like `mysqlのDialectをレビューして`, run the **full review** workflow below. Only use **quick review** when the user explicitly asks for a short/targeted check or supplies a clear time/usage cap; label it as limited.
- Honor the named DB, module, versions, files, commit, and any user constraints. Discover actual paths; do not assume class names or supported features.
- Review only: **do not edit tracked code, tests, build files, documentation, or dependencies** without explicit authorization. Keep probes/logs outside tracked source or in safely removable temporary locations. Preserve the user's uncommitted changes.
- Do not use production/external databases, create services/resources, download dependencies, or make destructive changes without authorization. If real DB testing is unavailable, continue with read-only inspection, generated-SQL/model probes, and available local tests.
- Work autonomously within the authorized scope. Do not repeatedly ask whether to continue. Ask only when a genuine blocker or approval-requiring action is reached.
- Match the user's language in the final report (Japanese for Japanese requests).

## Workflow — complete all four passes

### Pass 1 — inventory, plan, and risk map

1. Record the repository root, commit, dirty/clean working-tree status, module, Java/Gradle environment, available local test fixtures/DB versions, and constraints. Do not reset/clean or overwrite anything.
2. Identify the actual Dialect class hierarchy, version resolver, capability flags, type registrations, JDBC converters, metadata readers, DDL/DML factories, bulk operations, and inherited/shared code; discover downstream Dialects when applicable.
3. **Before concluding that anything is correct**, make a coverage ledger using the domains below. For each applicable domain write down at least: concrete entry-point classes/files, suspected high-risk paths and boundaries, intended verification, and status `not-started`.
4. Prioritize loss of data/meaning, schema recreation failures, bad SQL, compatibility regression, silent coercion/partial success, and transaction problems. Do not concentrate exclusively on one interesting subsystem.

### Pass 2 — breadth-first source review (mandatory)

Inspect **each applicable domain**, including at least one actual execution/call path or boundary case where meaningful. `grep`-only discovery, reading a class name, or running its existing test suite does **not** count as checked.

| Domain | Minimum questions to answer from the actual implementation |
| --- | --- |
| A. Versioning and flags | Which engine/driver versions resolve to which Dialect? Are feature introductions, inherited defaults, and SQL factories consistent at version boundaries? |
| B. Type registration | Are native names/aliases, fixed vs variable lengths, precision/scale, unsigned/signed types, native-to-model and model-to-native conversion covered? Does an unsupported type silently become `OTHER`? |
| C. JDBC read/bind/convert | Are values preserved across read → sqlapp model → write/bind? Consider null, noncanonical values, time units, fractional values, binary/bit, identity, enum, and platform-specific types. |
| D. Metadata and schema | Do readers preserve defaults, hidden/generated/identity properties, next values, sizes, constraints, ordering, and object definitions through model → DDL recreation? |
| E. DDL and schema change | Are CREATE/ALTER/DROP, indexes, constraints, partition changes, dependencies, no-op, increase/decrease, and reverse operations valid and lossless? |
| F. DML and SQL generation | Check SELECT/paging, INSERT/UPDATE/DELETE, MERGE/UPSERT, temp-table operations, quoting, empty lists, all-key/no-update-column tables, statement delimiters, and capability flags. |
| G. Bulk, batch, and transactions | Check data encoding, warnings, truncation, skipped rows, generated keys, reported counts, staging/upsert, rollback, cleanup, and partial failure semantics. |
| H. Version-dependent features | Inspect version-sensitive metadata/SQL for generated/invisible columns, JSON/spatial/temporal support, driver behaviors, and other actual engine features. |
| I. Shared code and derived Dialects | Follow consequential calls into sqlapp-core and applicable inherited/subclass Dialects; inspect possible cross-DB regressions. |
| J. Tests and assertions | What is covered, what boundaries are missing, and do expected SQL/test assertions accidentally endorse incorrect behavior? |

If a domain does not apply, mark `n/a` and explain why. If it applies but is not thoroughly inspected, mark `partial` **and identify specific uninspected classes/paths and why they remain**. `partial` is not an automatic permission to stop.

**Minimum breadth rule:** before finalizing a full review, perform a meaningful source-path check in *every* applicable domain. For high-risk domains B–G, inspect representative success **and** failure/boundary paths, not only the first implementation or first bug found. If genuinely blocked, record the blocker; do not quietly skip the domain.

### Pass 3 — focused validation and independent checks

Trace critical end-to-end invariants and try small, observable counterexamples:

- **Preserve values:** DB value → JDBC read → sqlapp value → conversion/serialization → bind/write → value. Include units, magnitude, null, sign, fractional precision, out-of-range/noncanonical inputs, and character/binary semantics where supported.
- **Preserve schema:** DB metadata → sqlapp schema/model → generated DDL → recreated metadata. Include length, precision, identity state, hidden/generated attributes, partition values, defaults, keys, and constraints.
- **Generate valid SQL:** Inspect the factory *and* its capability flag; compare output to the selected DB version. Test representative boundary cases such as empty collections, no mutable columns, renames, and both directions of a structural change.
- **Surface failures:** Check whether load/batch warnings, count differences, data coercion, ignored rows, and cleanup/rollback failures propagate to callers instead of being treated as success.
- **Respect supported versions:** Check both sides of claimed version boundaries, especially when a base class supplies behavior to older engines.

For each candidate issue, attempt the strongest **safe, feasible** evidence:

1. Trace the exact code path and expected behavior.
2. Produce a minimal local model/converter/generated-SQL probe when possible. A Java probe without DB connection is **not** an actual DB reproduction.
3. Use an already available, authorized disposable DB to verify execution, stored values, or metadata when feasible; state exact engine/driver version.
4. Consult authoritative vendor/JDBC documentation when accessible, and apply the specification to the correct version.
5. Run focused existing tests; where feasible, run the entire relevant module and applicable dependent modules. Inspect assertions, not just pass counts.

Classify evidence as **`DB reproduced`**, **`local code/SQL reproduced`**, **`code + official specification verified`**, or **`suspected / unverified`**. Report the actual observed input, expected output, actual output, and method. A warning in documentation, a generated SQL string, and a live reproduction are distinct things.

If given findings from another reviewer or model, verify them independently, identify false positives and design choices, and investigate nearby unreported failure paths. Do not accept the other review as ground truth.

### Pass 4 — adversarial coverage audit before stopping

**Do not jump directly from finding 2–6 defects to a final answer.** Before stopping:

1. Compare the results to the **Pass 1 ledger**, not to a target issue count. For each of A–J, record `checked` / `partial` / `blocked` / `n/a`, the **specific implementation path inspected**, and the strongest check performed. `checked` means meaningful review of representative code and boundaries, **not proof of all possible behavior**.
2. Identify uncovered **high-risk** paths within B–G; inspect those that are feasible without violating scope or permissions. Perform a **second sweep of overlooked domains** rather than repeatedly expanding only the area that already yielded bugs.
3. Specifically challenge early assumptions: Could code handle one combination of metadata attributes but not another? Can a successful test encode a wrong expected SQL string? Could read and write each work separately but fail as a round-trip? Could version inheritance change the result? Could an operation succeed while silently changing data?
4. If `partial` remains in a high-risk applicable domain, provide a **concrete reason** (such as missing DB version, unavailable dependency, prohibitive unsupported environment, user-imposed budget, or distinctly identified unreviewed branch) and list precisely what was not validated. Do not write merely “一部確認” with no path or reason.
5. Reconcile detected findings and overlapping root causes; keep independent failures distinct. Revisit severity with actual impact. Do not inflate categories just to appear comprehensive.
6. Only finish when the ledger and final findings are reconciled, all feasible high-risk breadth checks have been attempted, and the report distinguishes evidence from uncertainty. If external constraints genuinely force early termination, clearly label the result a **partial review**, list outstanding checks, and never imply completion.

**Do not impose an arbitrary minimum review duration, tool-call count, or bug quota.** The point is completing a defensible breadth check, not consuming unnecessary tokens. Within explicit user limits, prioritize the highest-risk items and report what was left.

## Final report — required sections

1. **Summary:** exact scope, highest-impact findings, number by severity **and evidence level**, whether source was modified, whether an actual DB was used.
2. **Findings ordered by impact:** `P1/P2/P3`, concise title, `path:line`, affected versions/calls, minimal trigger, **expected vs observed**, evidence level, risk, suggested fix and regression test. Put spec-only or suspected issues in appropriately labeled sections; avoid presenting them as reproduced.
3. **Coverage ledger A–J:** status, concrete paths inspected, how they were checked, remaining uninspected paths and the reason. Do not silently drop a relevant category.
4. **Verification:** exact test commands/results, probes, Java/Gradle/DB versions, vendor-document basis when used, artifact/log locations, and what was *not* run.
5. **Remaining risks / next steps:** risk-ranked and specific; distinguish not tested from tested and passed. Include inherited/related module effects if applicable.

Prioritize correctness and maintainer usefulness over stylistic advice. Keep explanations compact but complete. **Do not offer to start fixes before completing the review; do not alter code unless the user separately asks.**
