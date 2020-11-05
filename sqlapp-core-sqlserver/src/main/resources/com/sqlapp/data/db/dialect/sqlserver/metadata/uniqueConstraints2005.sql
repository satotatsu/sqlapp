SELECT
  DB_NAME() AS catalog_name
  , s.name as schema_name
  , OBJECT_NAME(kc.parent_object_id) AS table_name
  , kc.name AS constraint_name
  , i.name AS index_name
  , i.is_primary_key
  , o.type as ObjectType
  , i.object_Id AS id
  , c.user_type_id
  , c.column_id
  , i.Index_id
  , c.name AS column_name
  , ic.key_ordinal
  , i.name
  , i.type
  , i.is_padded AS pad_index
  , i.allow_row_locks
  , i.allow_page_locks
  , i.fill_factor
  , i.ignore_dup_key
  , i.is_disabled
  , ic.is_descending_key
  , ic.is_included_column 
  , st.no_recompute AS statistics_norecompute
  , INDEXPROPERTY(i.object_id, i.name,'IsAutoStatistics')
    AS auto_create_statistics
  , ds.name as index_file_group_name
FROM sys.key_constraints kc
INNER JOIN sys.indexes i
  ON (kc.parent_object_id=i.object_id
  AND kc.unique_index_id=i.index_id)
INNER JOIN sys.objects o
  ON (i.object_id=o.object_id) 
INNER JOIN sys.schemas s 
  ON (o.schema_id=s.schema_id) 
INNER JOIN sys.index_columns ic 
  ON (i.index_id=ic.index_id 
  AND i.object_id=ic.object_id)
INNER JOIN sys.columns c
  ON (ic.column_id=c.column_id 
  AND c.object_id=ic.object_id) 
LEFT OUTER JOIN sys.stats st
  ON (i.index_id=st.stats_id 
  AND i.object_id=st.object_id)
LEFT OUTER JOIN sys.data_spaces AS ds 
  ON (i.data_space_id=ds.data_space_id) 
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND s.name IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(constraintName) */
  AND kc.name IN /*constraintName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND OBJECT_NAME(kc.parent_object_id) IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY s.name, i.Name, ic.key_ordinal
