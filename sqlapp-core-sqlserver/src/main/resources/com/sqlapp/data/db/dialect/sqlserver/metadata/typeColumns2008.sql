SELECT
  DB_NAME() AS catalog_name
, SCHEMA_NAME(t.schema_id) AS schema_name
, t.name AS type_name
, t.is_nullable
, t.is_assembly_type
, t.is_table_type
, c.name AS column_name
, ty.name AS column_type_name
, c.user_type_id
, c.column_id AS id
, c.max_length
, c.precision
, c.scale
, c.collation_name
, c.is_nullable
, c.is_rowguidcol
, c.is_computed
, c.is_identity
, IDENT_SEED(st.name + '.' + t.name) AS ident_seed
, IDENT_INCR(st.name + '.' + t.name) AS ident_increment
, IDENT_CURRENT(st.name + '.' + t.name) AS ident_current
, cc.definition AS formula
, COALESCE(cc.is_persisted,0) AS formula_persisted
, CASE WHEN COALESCE(sd.column_id,0) = 0 THEN 0 ELSE 1 END AS has_computed_formula
, sx.name + '.' + xsc.name AS xmlschema
, c.is_xml_document
, ty.is_user_defined
, COALESCE(dc.object_id,0) AS default_id
, dc.name AS default_name
, dc.definition AS default_definition
, ch.definition AS check_definition
, ch.name AS check_constraint_name
, c.rule_object_id
, c.default_object_id 
, COLUMNPROPERTY ( t.type_table_object_id, c.name, 'IsIdNotForRepl') AS is_id_not_for_repl
, CAST(ex.value AS NVARCHAR(4000)) AS remarks
FROM sys.table_types t
INNER JOIN sys.objects o
  ON (t.type_table_object_id=o.object_id)
INNER JOIN sys.columns c
  ON (o.object_id=c.object_id)
INNER JOIN sys.schemas st
  ON (o.schema_id = st.schema_id)
INNER JOIN sys.types ty
  ON (c.user_type_id = ty.user_type_id)
LEFT OUTER JOIN sys.check_constraints ch
  ON (c.object_id=ch.parent_object_id
  AND c.column_id=ch.parent_column_id)
LEFT OUTER JOIN sys.xml_schema_collections xsc
  ON (c.xml_collection_id = xsc.xml_collection_id)
LEFT OUTER JOIN sys.schemas sx
  ON (xsc.schema_id=sx.schema_id)
LEFT OUTER JOIN sys.computed_columns cc
 ON (c.column_Id = cc.column_id
 AND c.object_id = cc.object_id)
LEFT OUTER JOIN sys.sql_dependencies sd
  ON (c.object_id = sd.referenced_major_id
  AND c.column_Id = sd.referenced_minor_id
  AND c.object_id = sd.object_id)
LEFT OUTER JOIN sys.default_constraints dc
  ON (o.object_id = dc.parent_object_id
  AND c.column_Id = dc.parent_column_id)
LEFT OUTER JOIN sys.extended_properties ex
  ON (o.object_id = ex.major_id
  AND c.column_Id = ex.minor_id)
WHERE 1=1
--  AND t.is_user_defined=1
  AND t.is_table_type=1
  /*if isNotEmpty(schemaName) */
  AND SCHEMA_NAME(t.schema_id) IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(typeName) */
  AND t.name IN /*typeName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY st.name, t.name
