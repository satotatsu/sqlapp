SELECT
    DB_NAME() AS catalog_name
  , dp.name AS role_name
  , dp.is_fixed_role
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
WHERE dp.is_fixed_role=0
  AND dp.type IN (
    'A'  --アプリケーション ロール
  , 'R'  --データベース ロール
  )
  /*if isNotEmpty(roleName) */
  AND dp.name IN /*roleName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY dp.name
