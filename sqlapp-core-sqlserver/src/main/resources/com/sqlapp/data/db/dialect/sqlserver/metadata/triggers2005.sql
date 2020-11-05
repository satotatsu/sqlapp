SELECT
  t.object_id
  , DB_NAME() AS catalog_name
  , s.name AS schema_name
  , t.name AS trigger_name
  , am.execute_as_principal_id
  , a.name AS assembly_name
  , am.assembly_class
  , am.assembly_id
  , am.assembly_method
  , t.type                   --TR=SQL Trigger,TA CLR Trigger
  , t.parent_class_desc
  , o.type AS owner_type     --U=User Define Table
  , OBJECT_NAME(t.parent_id) as parent_name
  , CAST(ISNULL(tei.object_id, 0) AS bit) AS is_insert
  , CAST(ISNULL(teu.object_id, 0) AS bit) AS is_update
  , CAST(ISNULL(ted.object_id, 0) AS bit) AS is_delete
  , tei.is_first AS insert_is_first
  , teu.is_first AS update_is_first
  , ted.is_first AS delete_is_first
  , tei.is_last AS insert_is_last
  , teu.is_last AS update_is_last
  , ted.is_last AS delete_is_last
  , t.parent_id              --0=DB Trigger,0≠DML Trigger
  , is_disabled
  , t.is_not_for_replication
  , t.is_instead_of_trigger    --1=INSTEAD OF,0 = AFTER
  , sm.definition
  , t.create_date
  , t.modify_date
FROM sys.triggers t
INNER JOIN sys.objects O 
  ON (o.object_id = t.parent_id)
INNER JOIN sys.schemas s 
  ON (o.schema_id=s.schema_id) 
LEFT OUTER JOIN sys.trigger_events AS tei 
  ON (tei.object_id = t.object_id 
    AND tei.type = 1)
LEFT OUTER JOIN sys.trigger_events AS teu 
  ON (teu.object_id = t.object_id 
    AND teu.type = 2)
LEFT OUTER JOIN sys.trigger_events AS ted 
  ON (ted.object_id = t.object_id 
    AND ted.type = 3)
LEFT OUTER JOIN sys.assembly_modules am 
  ON (t.object_id=am.object_id)
LEFT OUTER JOIN sys.assemblies a
  ON (am.assembly_id=a.assembly_id) 
LEFT OUTER JOIN sys.sql_modules sm
  ON (t.object_id = sm.object_id)
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND s.name IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(triggerName) */
  AND t.name IN /*triggerName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY s.name, t.name