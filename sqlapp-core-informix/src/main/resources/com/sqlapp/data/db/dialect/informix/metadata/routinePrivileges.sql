SELECT TRIM(DBINFO('dbname')) AS catalog_name, TRIM(p.owner) AS schema_name
, p.procname AS routine_name, p.specificname AS specific_name
, p.procid AS routine_id, p.numargs AS argument_count, p.paramtypes::LVARCHAR AS routine_parameter_types
, CASE WHEN p.isproc = 't' THEN 'PROCEDURE' ELSE 'FUNCTION' END AS routine_kind
, a.grantor, a.grantee, a.procauth AS routine_auth
FROM sysprocauth a
INNER JOIN sysprocedures p ON p.procid = a.procid
WHERE 1 = 1
  /*if isNotEmpty(catalogName)*/
  AND TRIM(DBINFO('dbname')) IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(objectName)*/
  AND p.procname IN /*objectName*/('%')
  /*end*/
ORDER BY p.owner, p.procname, p.procid, a.grantee, a.grantor
