SELECT
  gr.GRANTEE
, gr.GRANTEE_TYPE /*'USER' or 'ROLE'*/
, gr.ROLE_NAME
, gr.GRANTOR
, gr.IS_GRANTABLE /*Role was granted 'WITH ADMIN OPTION': 'TRUE', 'FALSE'*/
FROM GRANTED_ROLES gr
WHERE 1=1
  /*if isNotEmpty(grantee) */
  AND gr.GRANTEE IN /*grantee;type=NVARCHAR*/('%')
  /*end*/
ORDER BY gr.GRANTEE, gr.ROLE_NAME