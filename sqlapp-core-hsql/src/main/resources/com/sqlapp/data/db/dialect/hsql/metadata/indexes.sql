SELECT
   *
FROM information_schema.system_indexinfo si
WHERE 1=1
  /*if isNotEmpty(catalogName)*/
  AND si.table_cat IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND si.table_schem IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND si.table_name IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(indexName)*/
  AND si.index_name IN /*indexName*/('%')
  /*end*/
  AND NOT EXISTS
  (
    SELECT 1
    FROM information_schema.table_constraints tc
    WHERE tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE')
      AND tc.table_catalog=si.table_cat
      AND tc.table_schema=si.table_schem
      AND tc.table_name=si.table_name
      AND si.index_name =tc.constraint_name
  )
  AND NOT EXISTS
  (
    SELECT 1
    FROM INFORMATION_SCHEMA.SYSTEM_CROSSREFERENCE cr
    WHERE 1=1
      AND cr.FKTABLE_CAT=si.table_cat
      AND cr.FKTABLE_SCHEM=si.table_schem
      AND cr.FKTABLE_NAME=si.table_name
      AND cr.FK_NAME=si.index_name
  )
ORDER BY si.table_cat, si.table_schem, si.table_name, si.index_name, si.ordinal_position