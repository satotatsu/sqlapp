SELECT
  RDB$RELATION_NAME AS object_name
, RDB$GRANTOR AS grantor
, RDB$USER AS grantee
, RDB$PRIVILEGE AS privilege_type
, RDB$GRANT_OPTION AS is_grantable
FROM RDB$USER_PRIVILEGES
WHERE 1=1
  /*if isNotEmpty(objectName) */
  AND RDB$RELATION_NAME IN /*objectName*/('%')
  /*end*/
  AND RDB$FIELD_NAME IS NULL
  /*if readerOptions.excludeSystemObjects */
  AND RDB$RELATION_NAME NOT LIKE 'RDB$%'
  /*end*/
ORDER BY RDB$RELATION_NAME, RDB$PRIVILEGE
