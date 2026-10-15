SELECT V.* FROM ALL_JSON_DUALITY_VIEW_TAB_COLS V
WHERE 1=1
 /*if isNotEmpty(schemaName)*/
 AND V.VIEW_OWNER IN /*schemaName*/('%')
 /*end*/
 /*if isNotEmpty(tableName)*/
 AND V.VIEW_NAME IN /*tableName*/('%')
 /*end*/
ORDER BY V.VIEW_OWNER, V.VIEW_NAME, V.TABLE_NUMBER, V.COLUMN_NAME, V.JSON_KEY_NAME
