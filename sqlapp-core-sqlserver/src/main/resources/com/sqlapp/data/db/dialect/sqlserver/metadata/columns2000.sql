SELECT
    DB_NAME() AS catalog_name
  , su.name AS schema_name
  , so.id AS table_id
  , so.name AS table_name
  , c.id
  , c.name AS column_name
  , t.name AS type_name
  , c.length AS max_length
  , c.prec AS precision
  , c.scale
  , c.collation AS collation_name
  , c.isnullable AS is_nullable
  , c.iscomputed AS is_computed
  , c.colorder
  , cm.text AS default_definition
  , CASE c.status WHEN 128 THEN 1 ELSE 0 END AS is_identity
  , IDENT_SEED(su.name + '.' + so.name) AS ident_seed
  , IDENT_INCR(su.name + '.' + so.name) AS ident_increment
  , IDENT_CURRENT(su.name + '.' + so.name) AS ident_current
  , CAST(null AS NVARCHAR(4000)) AS remarks
FROM syscolumns c
INNER JOIN sysobjects so
  ON (c.id = so.id)
INNER JOIN sysusers su
  ON (so.uid = su.uid)
INNER JOIN systypes t
  ON (c.xusertype = t.xusertype)
LEFT OUTER JOIN sys.syscomments cm
  ON (c.cdefault = cm.id)
WHERE 1=1
--  AND SO.type = 'U'
  /*if isNotEmpty(schemaName) */
  AND su.name IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName) */
  AND so.name IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(columnName) */
  AND c.name IN /*columnName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY su.name, so.name, c.colorder