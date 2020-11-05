SELECT
*
FROM information_schema.table_privileges
WHERE 1=1
  /*if isNotEmpty(catalogName) */
  AND table_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName) */
  AND table_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(objectName) */
  AND table_name IN /*objectName*/('%')
  /*end*/
ORDER BY grantor, grantee, table_catalog, table_schema, table_name
