SELECT s.GRANTEE, s.TABLE_CATALOG, s.TABLE_SCHEMA, s.PRIVILEGE_TYPE, s.IS_GRANTABLE,
       0 AS PARTIAL_REVOKES
FROM information_schema.schema_privileges s
WHERE TRUE
  /*if isNotEmpty(catalogName)*/
  AND s.TABLE_CATALOG IN /*catalogName*/('def')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND s.TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
ORDER BY s.TABLE_CATALOG, s.TABLE_SCHEMA, s.GRANTEE, s.PRIVILEGE_TYPE
