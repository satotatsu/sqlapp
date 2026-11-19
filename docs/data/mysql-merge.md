# MySQL MERGE SQL generation

`SqlType.MERGE` generates a single `INSERT ... ON DUPLICATE KEY UPDATE`
statement. A duplicate value in any primary or unique key can cause an update,
as defined by MySQL. Selecting merge key columns controls which columns are
excluded from the update list; it does not limit the server's conflict detection
to that key. No additional rejection of tables with multiple unique keys is
performed. Table designers remain responsible for those uniqueness rules.

## Inserted row references

| Dialect version | Reference to an inserted value |
|---|---|
| MySQL before 8.0.19 | `VALUES(column)` |
| MySQL 8.0.19 and later, including 8.1–8.3, 8.4 and 9.x | Row and column aliases, such as `sqlapp_new_row.sqlapp_value_1` |
| MySQL 8.0 with an unknown patch version | `VALUES(column)` for compatibility with older patches |
| MariaDB | `VALUES(column)` |
| Aurora MySQL 3.x / MySQL 8.0 and Aurora MySQL 8.4 | Row alias |

MySQL introduced row aliases in
[8.0.19](https://dev.mysql.com/doc/refman/8.0/en/insert-on-duplicate.html)
and deprecated the `VALUES(column)` function in
[8.0.20](https://dev.mysql.com/doc/relnotes/mysql/8.0/en/news-8-0-20.html).
The `VALUES (...)` clause that supplies inserted rows remains supported.
Aurora MySQL 3.x starts from
[MySQL 8.0.23 compatibility](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/AuroraMySQL.MySQL80.html).

The MySQL resolver uses `MySql8019` for the 8.0.19 boundary. Both the normal and
numeric TINYINT(1) dialect chains include it. This is an additive dialect class;
existing public methods and configuration formats are unchanged. Generated SQL
text changes on supported newer versions, while insert/update semantics,
optimistic-lock expressions and empty-update handling are retained.

The inserted row alias is changed when it would match the target table name.
Separate inserted column aliases avoid collisions with the table's column
names, preserving existing unqualified expressions that refer to stored rows.
MariaDB keeps its existing syntax independently of its version number.
