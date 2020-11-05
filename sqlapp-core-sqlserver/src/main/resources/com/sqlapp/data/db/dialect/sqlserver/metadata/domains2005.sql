SELECT
  DB_NAME() AS catalog_name
, SCHEMA_NAME(t.schema_id) AS schema_name
, t.name AS domain_name
, t.max_length
, t.precision
, t.scale
, t.collation_name
, t.is_nullable
, t.is_assembly_type
--, t.default_object_id /*sp_bindefaultで作成するデフォルト(非推奨機能)*/
--, t.rule_object_id /*sp_bindruleで作成するルール(非推奨機能)*/
, t.is_table_type
, bt.name as base_type_name
, at.assembly_class
, asm.name AS assembly_name
--, af.content
--  , sm.definition
--  , sm.is_schema_bound
  , OBJECT_NAME(t.rule_object_id) AS rule_name
FROM sys.types t
INNER JOIN sys.types bt
  ON (t.system_type_id=bt.user_type_id
  AND t.name<>bt.name)
LEFT OUTER JOIN sys.sql_modules sm
  ON (t.rule_object_id=sm.object_id)
LEFT OUTER JOIN sys.assembly_types at
  ON (t.user_type_id = at.user_type_id
  AND t.is_assembly_type = 1)
LEFT OUTER JOIN sys.assemblies asm
  ON (at.assembly_id = asm.assembly_id) 
--LEFT OUTER JOIN sys.assembly_files af
--  ON (asm.assembly_id = af.assembly_id)
WHERE 1=1
--  AND t.is_user_defined=1
  AND t.is_table_type=0
  /*if isNotEmpty(schemaName) */
  AND SCHEMA_NAME(t.schema_id) IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(domainName) */
  AND t.name IN /*domainName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY SCHEMA_NAME(t.schema_id), t.name
