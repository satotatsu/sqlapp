SELECT RDB$ROLE_NAME AS role_name, RDB$OWNER_NAME AS owner_name,
       CAST(NULL AS VARCHAR(1)) AS remarks
FROM RDB$ROLES
WHERE 1=1
  /*if isNotEmpty(roleName) */
  AND RDB$ROLE_NAME IN /*roleName*/('%')
  /*end*/
ORDER BY RDB$ROLE_NAME
