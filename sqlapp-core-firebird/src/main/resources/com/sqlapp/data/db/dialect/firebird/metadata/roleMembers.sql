SELECT RDB$RELATION_NAME AS role_name, RDB$USER AS grantee,
       RDB$USER_TYPE AS grantee_type, RDB$GRANTOR AS grantor,
       RDB$GRANT_OPTION AS grant_option, RDB$FIELD_NAME AS default_marker
FROM RDB$USER_PRIVILEGES
WHERE RDB$PRIVILEGE = 'M' AND RDB$OBJECT_TYPE = 13
  /*if isNotEmpty(grantee) */
  AND RDB$USER IN /*grantee*/('%')
  /*end*/
  /*if readerOptions.excludeSystemObjects */
  AND RDB$RELATION_NAME NOT LIKE 'RDB$%'
  /*end*/
ORDER BY RDB$RELATION_NAME, RDB$USER, RDB$GRANTOR
