SELECT
    DB_NAME() AS catalog_name
  , dp.name AS user_name
  , dp.is_fixed_role
  , suser_sname(dp.sid) AS login_user_name
  , dp.type
  , dp.type_desc
  , dp.principal_id
  , dp.default_schema_name
  , dp.create_date
  , dp.modify_date
  , dp.owning_principal_id
  , odp.name AS owning_user_name
  , CASE odp.name WHEN 'dbo' THEN 1 ELSE 0 END AS is_system
FROM sys.database_principals dp
LEFT OUTER JOIN sys.database_principals odp
  ON (dp.owning_principal_id=odp.principal_id)
WHERE 1=1
--  AND dp.is_fixed_role=0
  AND dp.type IN (
    'S'  --SQL ユーザー
  , 'U'  --Windows ユーザー
  )
  /*if isNotEmpty(userName) */
  AND dp.name IN /*userName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY dp.name
