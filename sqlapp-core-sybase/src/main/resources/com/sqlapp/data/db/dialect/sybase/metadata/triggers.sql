SELECT
  DB_NAME() AS catalog_name
, USER_NAME(tr.uid) AS schema_name
, tr.id AS trigger_id
, tr.name AS trigger_name
, USER_NAME(tbl.uid) AS table_schema_name
, tbl.name AS table_name
, tr.crdate AS created
, c.text AS definition
, (ISNULL(c.status,0) & 1) AS source_hidden
, c.number AS source_group
FROM sysobjects tr
INNER JOIN sysobjects tbl ON tr.deltrig=tbl.id
INNER JOIN syscomments c ON tr.id=c.id AND c.texttype=0
WHERE tr.type='TR'
  /*if isNotEmpty(catalogName) */
  AND DB_NAME() IN /*catalogName;type=VARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(schemaName) */
  AND USER_NAME(tr.uid) IN /*schemaName;type=VARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(triggerName) */
  AND tr.name IN /*triggerName;type=VARCHAR*/('%')
  /*end*/
ORDER BY USER_NAME(tr.uid), tr.name, c.number, c.colid2, c.colid
