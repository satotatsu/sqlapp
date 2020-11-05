SELECT
  current_database() AS catalog_name
  , r.grantor
  , r.grantee
  , r.routine_schema
  , r.routine_name
  , r.specific_schema
  , r.specific_name
  , r.privilege_type
  , r.is_grantable
  , null AS with_hierarchy
  , p.pronargs
  , p.proargtypes
  , p.proargnames
  , p.oid AS proc_id
FROM information_schema.routine_privileges r
INNER JOIN pg_proc p
  ON (r.routine_name=p.proname
    AND r.specific_name=(p.proname || '_' || CAST(p.oid AS text)))
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND r.routine_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(objectName)*/
  AND r.routine_name IN /*objectName*/('%')
  /*end*/
ORDER BY r.grantor, r.grantee, r.specific_schema, r.specific_name
