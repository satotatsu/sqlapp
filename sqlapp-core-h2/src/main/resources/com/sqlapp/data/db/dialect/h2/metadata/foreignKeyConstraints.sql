SELECT
  cr.*
FROM INFORMATION_SCHEMA.CROSS_REFERENCES cr
WHERE TRUE
  /*if isNotEmpty(catalogName)*/
  AND cr.pktable_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND cr.pktable_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND cr.pktable_name IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(columnName)*/
  AND cr.pkcolumn_name IN /*columnName*/('%')
  /*end*/
ORDER BY cr.pktable_catalog, cr.pktable_schema, cr.pktable_name, cr.ordinal_position
