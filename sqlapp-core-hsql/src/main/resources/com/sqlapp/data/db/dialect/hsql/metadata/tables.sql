SELECT st.table_cat
, st.table_schem
, st.table_name
, st.table_type
, st.remarks
, st.hsqldb_type
, st.read_only
, st.commit_action
FROM information_schema.system_tables st
WHERE 1=1
  /*if isNotEmpty(catalogName)*/
  AND st.table_cat IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND st.table_schem IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND st.table_name IN /*tableName*/('%')
  /*end*/
  AND NOT EXISTS (
    SELECT 1
    FROM information_schema.views v
    WHERE 1=1
      AND st.table_cat=v.table_catalog
      AND st.table_schem=v.table_schema
      AND st.table_name=v.table_name
  )
ORDER BY st.table_cat, st.table_schem, st.table_name
