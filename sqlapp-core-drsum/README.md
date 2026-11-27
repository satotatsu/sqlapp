# sqlapp-core-drsum

Experimental dialect for WingArc1st **Dr.Sum** (Dr.Sum Server 5.x).

The dialect was built from the Dr.Sum 5.7 manual (SQL reference, system tables
and JDBC driver chapters). **It has not been run against a Dr.Sum server.** All
tests use an in-memory fake JDBC connection. Items marked *assumption* below
must be confirmed with a real server (see "Verifying against a server").

## Connection

The Dr.Sum JDBC driver (`dwodsjd4.jar`) ships with the Dr.Sum Development Kit
(`<Development Kit>\java\jdbc\dwodsjd4.jar`) and is not on Maven Central. This
module does not depend on it; put it on the runtime classpath yourself.

- Driver class: `jp.co.dw_sapporo.JDBC.JDBCDriver`
- URL: `jdbc:drsum://<host>:<port>;database=<db>` or `jdbc:dwods:<host>:<port>:<db>`

## What is supported

| Area | Behavior |
| --- | --- |
| Resolution | `DrSumDialectResolver` matches product names `Dr.Sum`, `DrSum`, `Dr Sum`, `Dr.Sum EA` (case-insensitive). *Assumption*: the driver's `getDatabaseProductName()` returns one of these. |
| Identifiers | Quoted with `[]`, case-sensitive, not case-converted. |
| Types (DDL) | INT/SMALLINT/TINYINT → `INTEGER` (Dr.Sum stores NUMERIC(33,0)); BIGINT → `NUMERIC(19,0)`; CHAR/VARCHAR/NVARCHAR(n ≤ 255) → `VARCHAR(n)`; longer text → unsized `VARCHAR` (65,535 chars); NUMERIC/DECIMAL(p,s) with p ≤ 33, s ≤ 31; unsized NUMERIC stays unsized (33-digit float); REAL/FLOAT/DOUBLE; DATE; TIME/TIMESTAMP/INTERVAL with optional 0–6 fractional digits. |
| Keys | A PRIMARY KEY is translated to constraints that enforce the same rule: one column → `NOT NULL UNIQUE`; several columns → `COMPOUND KEY <name>(...) NOT NULL UNIQUE`. A one-column UNIQUE → column `UNIQUE`; a multi-column UNIQUE → `COMPOUND KEY <name>(...) UNIQUE`. |
| Indexes | `CREATE INDEX <name> ON <table>(<column>)`, `DROP INDEX <name>` (single-column, non-unique search indexes). |
| Comments | `ALTER TABLE t MODIFY COMMENT '...'`, `ALTER TABLE t MODIFY c COMMENT '...'`. |
| Views | Default `CREATE VIEW ... AS <select>` / `DROP VIEW`. |
| Metadata | Tables, views, columns, single-column UNIQUE, search indexes, table/column comments from `__all_tables__` and `__all_indexes__`; each reader issues one query regardless of the number of tables. |

## What is rejected (instead of generating wrong SQL)

FOREIGN KEY, CHECK and exclusion constraints; identity, computed, array and
binary columns; temporary or partitioned tables; unique, multi-column or
partial indexes; VARCHAR longer than 65,535; fractional seconds over 6;
comments over 1,023 characters or containing tabs/line breaks; a multi-column
key without a name or named like a column. ALTER TABLE generation (schema
diff) is not implemented and throws `UnsupportedOperationException`.

Column character set and collation are not emitted: Dr.Sum stores all strings
as UTF-8 and has no such column attribute. BOOLEAN follows sqlapp's standard
fallback (`INTEGER`).

## Known limitations of the metadata reader

- `__all_tables__` encoding of `column_null` / `column_unique` is undocumented.
  *Assumption*: flags `1/Y/YES/T/TRUE` vs `0/N/NO/F/FALSE`/NULL, and
  `column_null` set means NOT NULL. Any other value fails with an error asking
  for the value.
- *Assumption*: a row with an empty `column_name` is the table-level row and
  carries the table comment; `view_select` is set for views; `view_connect`
  is set for multiviews (skipped, not modeled).
- *Assumption*: `__all_tables__` returns columns in definition order (it has
  no ordinal column).
- *Assumption*: `column_precision` holds VARCHAR length, NUMERIC precision and
  TIME/TIMESTAMP/INTERVAL fractional digits; an unsized VARCHAR (0 or NULL) is
  read as 65,535 characters so it is never narrowed.
- Not read: column default values, COMPOUND KEY definitions (not exposed by
  the documented system tables), join indexes, link tables, multiviews,
  distributors, in-memory settings, privileges. The raw `assortment` and index
  `status` are kept in the `drsum.assortment` / `drsum.status` specifics.

## Verifying against a server

`tools/DrSumProbe.java` is a read-only program (SELECT and DatabaseMetaData
only) that prints the values the assumptions above depend on:

```
java -cp dwodsjd4.jar sqlapp-core-drsum/tools/DrSumProbe.java "jdbc:drsum://host:6001;database=SALES" user password [table]
```

Create a test table covering the cases first, for example:

```sql
CREATE TABLE probe_t (
  id INT NOT NULL UNIQUE, name VARCHAR(20), memo VARCHAR, amt NUMERIC(10,2),
  f NUMERIC, r REAL, d DATE, tm TIME(3), ts TIMESTAMP(2), iv INTERVAL,
  qty INT DEFAULT 0,
  COMPOUND KEY ck_probe (name, d) UNIQUE
);
ALTER TABLE probe_t MODIFY COMMENT 'table comment';
ALTER TABLE probe_t MODIFY name COMMENT 'column comment';
CREATE INDEX ix_probe_d ON probe_t(d);
CREATE VIEW probe_v AS SELECT id, name FROM probe_t;
```
