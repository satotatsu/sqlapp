select
    DB_NAME() AS catalog_name
  , s.name as schema_name
  , p.name as procedure_name
  , p.type
  , p.object_id
  , p.create_date
  , p.modify_date
  ,
  CASE COALESCE(sm.execute_as_principal_id, am.execute_as_principal_id)
  WHEN -2 THEN 'OWNER'
  ELSE
    COALESCE(dp.name, sp.name, 'CALLER')
  END 
  AS execute_as
  , sm.definition
  , sm.is_recompiled
  , sm.uses_ansi_nulls
  , sm.is_schema_bound
  , af.name AS assembly_name
  , am.assembly_class
  , am.assembly_id
  , am.assembly_method
FROM sys.procedures p
INNER JOIN sys.schemas s
  ON (s.schema_id = p.schema_id)
LEFT OUTER JOIN sys.assembly_modules am
  ON (p.object_id=am.object_id)
LEFT OUTER JOIN sys.assemblies af
  ON (am.assembly_id=af.assembly_id)
LEFT OUTER JOIN sys.sql_modules sm
  ON (p.object_id = sm.object_id)
LEFT OUTER JOIN sys.database_principals dp
  ON (dp.principal_id=COALESCE(sm.execute_as_principal_id, am.execute_as_principal_id))  
LEFT OUTER JOIN sys.server_principals sp
  ON (sp.principal_id=COALESCE(sm.execute_as_principal_id, am.execute_as_principal_id))
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND s.name IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(procedureName) */
  AND p.name IN /*procedureName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY s.name, p.name