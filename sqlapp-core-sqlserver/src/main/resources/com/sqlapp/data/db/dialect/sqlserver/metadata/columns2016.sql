SELECT
      DB_NAME() AS catalog_name
    , st.name AS schema_name
	, t.name AS table_name
	, c.name AS column_name
	, ty.name AS type_name
	, c.is_filestream
	, c.is_sparse
	, c.user_type_id
	, c.column_id AS id
	, c.max_length
	, c.precision
	, c.scale
	, c.collation_name AS collation
	, c.is_nullable
	, c.is_rowguidcol
	, c.is_computed
	, c.is_identity
	, COLUMNPROPERTY(t.object_id,c.name,'IsIdNotForRepl') AS is_not_for_replication
	, IDENT_SEED(st.name + '.' + t.name) AS ident_seed
	, IDENT_INCR(st.name + '.' + t.name) AS ident_increment
	, IDENT_CURRENT(st.name + '.' + t.name) AS ident_current
	, cc.definition AS formula
	, COALESCE(cc.is_persisted,0) AS formula_persisted
	, CASE WHEN COALESCE(sd.column_id,0) = 0 THEN 0 ELSE 1 END AS has_computed_formula
	, sx.name + '.' + xsc.name AS xmlschema
	, c.is_xml_document
	, ty.is_user_defined
	, t.object_id AS table_id
	, COALESCE(dc.object_id,0) AS default_id
	, dc.name AS default_name
	, dc.definition AS default_definition
	, ch.definition AS check_definition
	, ch.name AS check_constraint_name
	, c.rule_object_id
	, c.default_object_id
	, (CASE WHEN COALESCE(ctt.is_track_columns_updated_on,0) <> 0 THEN ctt.is_track_columns_updated_on ELSE 0 END) AS is_track_columns_updated_on
	, (CASE WHEN COALESCE(ctt.object_id,0) <> 0 THEN 1 ELSE 0 END) AS has_change_tracking
	, CAST(ex.value AS NVARCHAR(4000)) AS remarks
	, mc.masking_function
FROM sys.columns c
INNER JOIN sys.tables t
  ON (c.object_id = t.object_id)
INNER JOIN sys.types ty
  ON (c.user_type_id = ty.user_type_id)
INNER JOIN sys.schemas st
  ON (t.schema_id = st.schema_id)
LEFT OUTER JOIN sys.xml_schema_collections xsc
  ON (c.xml_collection_id = xsc.xml_collection_id)
LEFT OUTER JOIN sys.schemas sx
  ON (sx.schema_id = xsc.schema_id)
LEFT OUTER JOIN sys.check_constraints ch
  ON (c.object_id=ch.parent_object_id
  AND c.column_id=ch.parent_column_id)
LEFT OUTER JOIN sys.computed_columns cc
 ON (c.column_Id = cc.column_id
 AND c.object_id = cc.object_id)
LEFT OUTER JOIN sys.sql_dependencies sd
  ON (c.object_id = sd.referenced_major_id
  AND c.column_Id = sd.referenced_minor_id
  AND c.object_id = sd.object_id)
LEFT OUTER JOIN sys.default_constraints dc
  ON (t.object_id = dc.parent_object_id
  AND c.column_Id = dc.parent_column_id)
LEFT OUTER JOIN sys.change_tracking_tables ctt
  ON (t.object_id = ctt.object_id)
LEFT OUTER JOIN sys.extended_properties ex
  ON (t.object_id = ex.major_id
  AND c.column_Id = ex.minor_id)
LEFT OUTER JOIN sys.masked_columns mc
  ON (c.object_id = mc.object_id)
  AND c.column_Id = mc.column_id)
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND st.name IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName) */
  AND t.name IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(columnName) */
  AND c.name IN /*columnName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY st.name, t.name, c.column_id
