SELECT st.table_cat, st.table_schem, st.table_name, st.table_type, st.remarks,
       st.hsqldb_type, st.read_only, st.commit_action,
       tt.TABLE_NAME AS TEXT_TABLE_NAME, tt.DATA_SOURCE_DEFINTION AS TEXT_SOURCE, tt.IS_DESC AS TEXT_DESC
FROM information_schema.system_tables st
LEFT JOIN information_schema.system_texttables tt
  ON st.table_cat = tt.table_cat AND st.table_schem = tt.table_schem AND st.table_name = tt.table_name
WHERE TRUE
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
    SELECT 1 FROM information_schema.views v
    WHERE st.table_cat = v.table_catalog AND st.table_schem = v.table_schema AND st.table_name = v.table_name
  )
ORDER BY st.table_cat, st.table_schem, st.table_name
