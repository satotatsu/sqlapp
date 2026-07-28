SELECT
    p.TABSCHEMA AS schema_name
  , p.TABNAME AS table_name
  , p.PERIODNAME AS period_name
  , p.BEGINCOLNAME AS begin_column_name
  , p.ENDCOLNAME AS end_column_name
  , p.PERIODTYPE AS period_type
  , p.HISTORYTABSCHEMA AS history_table_schema_name
  , p.HISTORYTABNAME AS history_table_name
FROM SYSCAT.PERIODS p
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND rtrim(p.TABSCHEMA) IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND rtrim(p.TABNAME) IN /*tableName*/('%')
  /*end*/
ORDER BY p.TABSCHEMA, p.TABNAME, p.PERIODNAME
WITH UR
