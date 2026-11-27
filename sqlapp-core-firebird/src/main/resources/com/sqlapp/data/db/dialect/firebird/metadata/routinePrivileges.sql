SELECT RDB$RELATION_NAME AS object_name, RDB$OBJECT_TYPE AS object_type,
       RDB$USER AS grantee, RDB$USER_TYPE AS grantee_type,
       RDB$GRANTOR AS grantor, RDB$PRIVILEGE AS privilege_type,
       RDB$GRANT_OPTION AS is_grantable
FROM RDB$USER_PRIVILEGES
WHERE RDB$PRIVILEGE = 'X' AND RDB$OBJECT_TYPE IN (5, 15, 18)
  AND RDB$FIELD_NAME IS NULL
  /*if isNotEmpty(objectName) */
  AND RDB$RELATION_NAME IN /*objectName*/('%')
  /*end*/
  /*if readerOptions.excludeSystemObjects */
  AND RDB$RELATION_NAME NOT LIKE 'RDB$%'
  /*end*/
ORDER BY RDB$OBJECT_TYPE, RDB$RELATION_NAME, RDB$USER, RDB$GRANTOR
