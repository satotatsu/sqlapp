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
  , NULL AS collation_name
  , CASE WHEN (c.status & 8) = 8 THEN 1 ELSE 0 END AS is_nullable
  , 0 AS is_computed
  , c.colid AS colorder
  , cm.text AS default_definition
  , CASE c.status WHEN 128 THEN 1 ELSE 0 END AS is_identity
  , NULL AS ident_seed
  , NULL AS ident_increment
  , NULL AS ident_current
  , CAST(NULL AS VARCHAR(255)) AS remarks
FROM syscolumns c
INNER JOIN sysobjects so
  ON (c.id = so.id)
INNER JOIN sysusers su
  ON (so.uid = su.uid)
INNER JOIN systypes t
  ON (c.usertype = t.usertype)
LEFT OUTER JOIN syscomments cm
  ON (c.cdefault = cm.id)
WHERE 1=1
--  AND SO.type = 'U'
  /*if isNotEmpty(schemaName) */
  AND su.name IN /*schemaName;type=VARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName) */
  AND so.name IN /*tableName;type=VARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(columnName) */
  AND c.name IN /*columnName;type=VARCHAR*/('%')
  /*end*/
ORDER BY su.name, so.name, c.colid
