SELECT
  g.grantee
, g.object_name AS role_name
, g.grantor
FROM V_CATALOG.GRANTS g
WHERE g.object_type='ROLE'
  /*if isNotEmpty(grantee) */
  AND g.grantee IN /*grantee*/('%')
  /*end*/
ORDER BY g.grantee, g.object_name
