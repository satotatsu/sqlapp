SELECT
  RDB$RELATION_NAME AS object_name
, RDB$FIELD_NAME AS column_name
, RDB$GRANTOR AS grantor
, RDB$USER AS grantee
, RDB$PRIVILEGE AS privilege_type
, RDB$GRANT_OPTION AS is_grantable
FROM RDB$USER_PRIVILEGES
WHERE 1=1
  /*if isNotEmpty(objectName) */
  AND RDB$RELATION_NAME IN /*objectName*/('%')
  /*end*/
  AND RDB$FIELD_NAME IS NOT NULL
  /*if isNotEmpty(columnName)*/
  AND RDB$FIELD_NAME IN /*columnName*/('%')
  /*end*/
  /*if readerOptions.excludeSystemObjects */
  AND RDB$RELATION_NAME NOT LIKE 'RDB$%'
  /*end*/
ORDER BY RDB$RELATION_NAME, RDB$FIELD_NAME, RDB$PRIVILEGE
