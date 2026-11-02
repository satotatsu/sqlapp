SELECT TABLE_CATALOG, TABLE_SCHEMA, TABLE_NAME, `PERIOD`, START_COLUMN_NAME, END_COLUMN_NAME
FROM information_schema.PERIODS
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND TABLE_NAME IN /*tableName*/('%')
  /*end*/
ORDER BY TABLE_CATALOG, TABLE_SCHEMA, TABLE_NAME, `PERIOD`
