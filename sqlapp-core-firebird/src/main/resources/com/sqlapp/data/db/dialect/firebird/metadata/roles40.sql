SELECT RDB$ROLE_NAME AS role_name, RDB$OWNER_NAME AS owner_name,
       RDB$DESCRIPTION AS remarks, RDB$SYSTEM_PRIVILEGES AS system_privileges
FROM RDB$ROLES
WHERE 1=1
  /*if isNotEmpty(roleName) */
  AND RDB$ROLE_NAME IN /*roleName*/('%')
  /*end*/
  /*if readerOptions.excludeSystemObjects */
  AND COALESCE(RDB$SYSTEM_FLAG, 0) = 0
  /*end*/
ORDER BY RDB$ROLE_NAME
