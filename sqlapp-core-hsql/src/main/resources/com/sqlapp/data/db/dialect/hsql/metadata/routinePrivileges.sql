SELECT
    r.*
FROM information_schema.routine_privileges r
WHERE 1=1
  /*if isNotEmpty(catalogName)*/
  AND r.routine_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND r.routine_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(objectName)*/
  AND r.routine_name IN /*objectName*/('%')
  /*end*/
ORDER BY r.grantor, r.grantee, r.routine_schema, r.routine_name, r.specific_name
