SELECT 
  DB_NAME() AS catalog_name
, u.name AS schema_name
, t.name AS domain_name
, t.allownulls
, t.length
, t.prec
, t.scale
, NULL as collation_name
, bt.name as base_type_name
FROM systypes t
INNER JOIN systypes bt
  ON (t.type=bt.type
     AND bt.usertype=(
       SELECT min(bt2.usertype)
       FROM systypes bt2
       WHERE bt2.type=t.type
     ))
INNER JOIN sysusers u
  ON (t.uid=u.uid)
WHERE t.name not in
  (
	  'image'
	, 'text'
	, 'uniqueidentifier'
	, 'date'
	, 'time'
	, 'datetime2'
	, 'datetimeoffset'
	, 'tinyint'
	, 'smallint'
	, 'int'
	, 'smalldatetime'
	, 'real'
	, 'money'
	, 'datetime'
	, 'float'
	, 'sql_variant'
	, 'ntext'
	, 'bit'
	, 'decimal'
	, 'numeric'
	, 'smallmoney'
	, 'bigint'
	, 'hierarchyid'
	, 'geometry'
	, 'geography'
	, 'varbinary'
	, 'varchar'
	, 'binary'
	, 'char'
	, 'timestamp'
	, 'nvarchar'
	, 'nchar'
	, 'xml'
	, 'sysname'
  )
  /*if isNotEmpty(schemaName) */
  AND u.name IN /*schemaName;type=VARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(domainName) */
  AND t.name IN /*domainName;type=VARCHAR*/('%')
  /*end*/
ORDER BY t.name
