SELECT
  DB_NAME() AS catalog_name
, su.name AS schema_name
, OBJECT_NAME(c.constrid) AS constraint_name
, OBJECT_NAME(c.tableid) AS table_name
, COL_NAME(c.tableid, c.colid) AS column_name
, sc.text AS definition
, (ISNULL(sc.status,0) & 1) AS source_hidden
, sc.number AS source_group
, CASE WHEN c.colid > 0 THEN 1 ELSE 0 END AS is_column_check_constraint
FROM sysconstraints c
INNER JOIN sysobjects so
  ON (c.tableid=so.id)
INNER JOIN sysusers su
  ON (so.uid = su.uid)
INNER JOIN syscomments sc
  ON (c.constrid = sc.id AND sc.texttype=0)
WHERE (c.status & 128)=128
  AND (c.status & 1)=0
  /*if isNotEmpty(schemaName)*/
  AND su.name IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND OBJECT_NAME(c.tableid) IN /*tableName*/('%')
  /*end*/
ORDER BY su.name, OBJECT_NAME(c.tableid), OBJECT_NAME(c.constrid), sc.number, sc.colid2, sc.colid
