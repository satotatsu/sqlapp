SELECT o.* FROM (
  SELECT TABSCHEMA AS schema_name, TABNAME AS table_name,
         CAST(NULL AS VARCHAR(128)) AS column_name, OPTION AS option_name, SETTING
  FROM SYSCAT.TABOPTIONS
  UNION ALL
  SELECT TABSCHEMA, TABNAME, COLNAME, OPTION, SETTING FROM SYSCAT.COLOPTIONS
) o
WHERE 1=1
/*if isNotEmpty(schemaName)*/
  AND rtrim(o.schema_name) IN /*schemaName*/('%')
/*end*/
/*if isNotEmpty(tableName)*/
  AND rtrim(o.table_name) IN /*tableName*/('%')
/*end*/
ORDER BY o.schema_name, o.table_name, o.column_name, o.option_name
WITH UR
