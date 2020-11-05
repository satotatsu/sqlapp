SELECT
  tc.*
  , cc.COMMENTS
  , tic.GENERATION_TYPE
  , tic.IDENTITY_OPTIONS
FROM all_tab_cols tc
LEFT OUTER JOIN all_tab_identity_cols tic
ON (
     tc.OWNER=tic.OWNER
     AND
     tc.TABLE_NAME=tic.TABLE_NAME
     AND
     tc.COLUMN_NAME=tic.COLUMN_NAME
    )
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
