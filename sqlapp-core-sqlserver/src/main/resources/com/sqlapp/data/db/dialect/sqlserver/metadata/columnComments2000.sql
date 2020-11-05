SELECT
  DB_NAME() AS catalog_name
, objtype
, objname
, name
, CAST(value AS NVARCHAR(4000)) AS value
FROM ::fn_listextendedproperty (NULL
, 'user'
/*if isNotEmpty(schemaName) */
,/*schemaName;type=NVARCHAR*/'dbo'
/*end*/
, 'table'
, /*tableName;type=NVARCHAR*/N'Table_2'
, 'column'
, default)