SELECT * FROM (
  SELECT a.TYPESCHEMA AS schema_name, a.TYPENAME AS type_name, a.ATTR_NAME AS column_name,
         a.ATTR_TYPENAME AS data_type, a.ATTR_TYPESCHEMA AS data_type_schema,
         a.ATTR_TYPEMODULENAME AS data_type_module,
         a.LENGTH, a.SCALE, a.CODEPAGE, a.TYPESTRINGUNITS, a.STRINGUNITSLENGTH,
         a.COLLATIONSCHEMA, a.COLLATIONNAME, a.LOGGED, a.COMPACT, a.ORDINAL
  FROM SYSCAT.ATTRIBUTES a
  WHERE a.TYPEMODULENAME IS NULL
    /*if isNotEmpty(schemaName) */
    AND a.TYPESCHEMA IN /*schemaName*/('%')
    /*end*/
    /*if isNotEmpty(typeName) */
    AND a.TYPENAME IN /*typeName*/('%')
    /*end*/
  UNION ALL
  SELECT a.TYPESCHEMA, a.TYPENAME, a.FIELDNAME, a.FIELDTYPENAME, a.FIELDTYPESCHEMA, a.FIELDTYPEMODULENAME,
         a.LENGTH, a.SCALE, a.CODEPAGE, a.TYPESTRINGUNITS, a.STRINGUNITSLENGTH,
         a.COLLATIONSCHEMA, a.COLLATIONNAME, CAST(NULL AS CHAR(1)), CAST(NULL AS CHAR(1)), a.ORDINAL
  FROM SYSCAT.ROWFIELDS a
  WHERE a.TYPEMODULENAME IS NULL
    /*if isNotEmpty(schemaName) */
    AND a.TYPESCHEMA IN /*schemaName*/('%')
    /*end*/
    /*if isNotEmpty(typeName) */
    AND a.TYPENAME IN /*typeName*/('%')
    /*end*/
) fields
ORDER BY fields.schema_name, fields.type_name, fields.ORDINAL
WITH UR
