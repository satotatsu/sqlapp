SELECT
  DB_NAME() AS catalog_name
, su.name AS schema_name
, OBJECT_NAME(c.constid) AS constraint_name
, OBJECT_NAME(c.id) AS table_name
, COL_NAME(c.id, c.colid) AS column_name
, OBJECT_DEFINITION(c.constid) AS definition
, OBJECTPROPERTY(c.constid, 'CnstIsColumn')  --単一の列に対するチェック制約(1 or 0)
  AS is_column_check_constraint
, c.spare1
, c.actions
, c.error
FROM sysconstraints c
INNER JOIN sysobjects so
  ON (c.id=so.id)
INNER JOIN sysusers su
  ON (so.uid = su.uid)
WHERE (c.status & 4)=4
  AND (c.status & 1)=0
  /*if isNotEmpty(schemaName)*/
  AND su.name IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND OBJECT_NAME(c.id) IN /*tableName*/('%')
  /*end*/
ORDER BY su.name, OBJECT_NAME(c.id), OBJECT_NAME(c.constid)