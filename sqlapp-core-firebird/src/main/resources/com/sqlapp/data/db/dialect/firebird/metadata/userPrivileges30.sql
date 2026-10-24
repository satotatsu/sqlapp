SELECT RDB$USER AS grantee, RDB$USER_TYPE AS grantee_type,
       RDB$GRANTOR AS grantor, RDB$GRANT_OPTION AS grant_option,
       RDB$PRIVILEGE AS privilege_type, RDB$OBJECT_TYPE AS object_type
FROM RDB$USER_PRIVILEGES
WHERE RDB$OBJECT_TYPE >= 20 AND RDB$PRIVILEGE IN ('C', 'L', 'O')
  /*if isNotEmpty(userName) */
  AND RDB$USER IN /*userName*/('%')
  /*end*/
ORDER BY RDB$USER, RDB$OBJECT_TYPE, RDB$PRIVILEGE, RDB$GRANTOR
