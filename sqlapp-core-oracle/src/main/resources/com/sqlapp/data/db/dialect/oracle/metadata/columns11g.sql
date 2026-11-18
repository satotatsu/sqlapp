SELECT
  tc.*
  , ic.coll_type AS SQLAPP_INTERVAL_COLLECTION_KIND
FROM all_tab_cols tc
LEFT OUTER JOIN all_coll_types ic
ON (ic.OWNER=tc.DATA_TYPE_OWNER AND ic.TYPE_NAME=tc.DATA_TYPE
    AND ic.ELEM_TYPE_NAME IN ('INTERVAL YEAR TO MONTH','INTERVAL DAY TO SECOND'))
WHERE 1=1
  /*if isNotEmpty(containsHiddenColumns)*/
  /*if !containsHiddenColumns*/
  AND tc.HIDDEN_COLUMN='NO'
  /*end*/
  /*if containsHiddenColumns*/
  AND tc.HIDDEN_COLUMN='YES'
  /*end*/
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND tc.OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND tc.TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(columnName)*/
  AND tc.COLUMN_NAME IN /*columnName*/('%')
  /*end*/
ORDER BY tc.OWNER, tc.TABLE_NAME, tc.COLUMN_ID
