SELECT
  TRIM(DBINFO('dbname')) AS catalog_name
, TRIM(p.owner) AS schema_name
, p.procid AS routine_id
, p.procname AS routine_name
, p.specificname AS specific_name
, p.numargs AS argument_count
, p.paramtypes::LVARCHAR AS routine_parameter_types
, b.data AS routine_definition
, b.datakey AS routine_text_kind
FROM sysprocedures p
LEFT JOIN sysprocbody b
  ON (p.procid = b.procid AND b.datakey IN ('T','A'))
WHERE p.isproc = 'f'
  AND LOWER(p.mode) <> 't'
  /*if isNotEmpty(schemaName)*/
  AND p.owner IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(functionName)*/
  AND p.procname IN /*functionName*/('%')
  /*end*/
ORDER BY p.owner, p.procname, p.procid, b.datakey DESC, b.seqno
