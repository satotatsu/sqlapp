SELECT
    *
FROM DBA_ROLES R
WHERE 1=1
  /*if isNotEmpty(roleName) */
  AND ROLE IN /*roleName*/('%')
  /*end*/
ORDER BY ROLE
