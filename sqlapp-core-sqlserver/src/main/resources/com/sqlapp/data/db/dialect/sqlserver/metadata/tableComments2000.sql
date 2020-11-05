SELECT 
 objtype
, objname
, name
, CAST(value AS NVARCHAR(4000)) AS value
FROM ::fn_listextendedproperty ('MS_Description'
, 'user'
/*if isNotEmpty(schemaName) */
,/*schemaName;type=NVARCHAR*/'dbo'
/*end*/
, 'table'
, default, default, default)
