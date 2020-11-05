/*非推奨(チェック制約の代わりに使う)の機能*/
SELECT
  DB_NAME() AS catalog_name
, SCHEMA_NAME(o.schema_id) AS schema_name
, name AS rule_name
, ISNULL(smod.definition, ssmod.definition) AS definition 
, o.object_id
FROM sys.objects o
LEFT OUTER JOIN sys.sql_modules smod
  ON (o.object_id=smod.object_id)
LEFT OUTER JOIN sys.system_sql_modules ssmod
  ON (o.object_id=ssmod.object_id)
WHERE o.type='R'
  /*if isNotEmpty(schemaName) */
  AND SCHEMA_NAME(o.schema_id) IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(ruleName) */
  AND o.name IN /*ruleName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY SCHEMA_NAME(o.schema_id), o.name
