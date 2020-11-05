SELECT
  cr.*
FROM INFORMATION_SCHEMA.SYSTEM_CROSSREFERENCE cr
WHERE TRUE
  /*if isNotEmpty(catalogName)*/
  AND cr.fktable_cat IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND cr.fktable_schem IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND cr.fktable_name IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(columnName)*/
  AND cr.fkcolumn_name IN /*columnName*/('%')
  /*end*/
ORDER BY cr.fktable_cat, cr.fktable_schem, cr.fktable_name, cr.key_seq
