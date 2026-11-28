# MySQL TEXT type boundaries

MySQL, MariaDB and Aurora MySQL dialects register the following TEXT type
capacities. When generating a type from `LONGVARCHAR` and a requested length,
the dialect selects the smallest registered type that can accommodate it.

| Type | Registered maximum |
|---|---:|
| TINYTEXT | 255 |
| TEXT | 65,535 |
| MEDIUMTEXT | 16,777,215 |
| LONGTEXT | 4,294,967,295 |

In particular, a requested length of 16,777,216 selects `LONGTEXT`, rather than
`MEDIUMTEXT`. These are storage ceilings, not a guarantee that this many
characters can be stored with every character set. Multibyte characters reduce
the effective capacity; packet limits and available memory also constrain
large values. See the [MySQL string type reference](https://dev.mysql.com/doc/refman/8.4/en/string-type-syntax.html).

This corrects the MEDIUMTEXT and LONGTEXT registrations inherited by all
MySQL-derived dialects. It does not change public APIs, configuration formats,
or explicitly specified native type names, and does not add strict validation
for requests exceeding the largest available type.

## MySQL version resolution

MySQL 8.1 through 8.3 use `MySql8019`, the nearest preceding implementation.
MySQL 8.0.0 and 8.0 with an unknown patch version retain `MySql800`; 8.0.1 and
patch versions through 8.0.18 use `MySql801`; 8.0.19 and later use `MySql8019`.
MySQL 8.4 and later 8.x versions retain
`MySql840`, and 9.x retains `MySql900`.

`MySql801` adds no behavior beyond `MySql800`. `MySql8019` enables the
[MERGE row alias syntax](mysql-merge.md). MariaDB and Aurora MySQL retain their
own resolver boundaries.
