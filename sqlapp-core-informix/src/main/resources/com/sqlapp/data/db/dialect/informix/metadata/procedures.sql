SELECT
  DBINFO('dbname') AS catalog_name
, p.owner AS schema_name
, p.procid AS routine_id
, p.procname AS routine_name
, p.specificname AS specific_name
, b.data AS routine_definition
FROM sysprocedures p
LEFT JOIN sysprocbody b
  ON (p.procid = b.procid AND b.datakey = 'T')
WHERE p.isproc = 't'
  AND LOWER(p.mode) <> 't'
  /*if isNotEmpty(schemaName)*/
  AND p.owner IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(procedureName)*/
  AND p.procname IN /*procedureName*/('%')
  /*end*/
ORDER BY p.owner, p.procname, p.procid, b.seqno
