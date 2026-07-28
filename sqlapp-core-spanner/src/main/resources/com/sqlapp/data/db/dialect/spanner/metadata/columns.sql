SELECT 
col.*
, (
    SELECT opt.OPTION_VALUE
    FROM INFORMATION_SCHEMA.COLUMN_OPTIONS opt
    WHERE opt.TABLE_SCHEMA=col.TABLE_SCHEMA
      AND opt.TABLE_NAME=col.TABLE_NAME
      AND opt.COLUMN_NAME=col.COLUMN_NAME
      AND opt.OPTION_NAME='allow_commit_timestamp'
  ) AS ALLOW_COMMIT_TIMESTAMP
FROM INFORMATION_SCHEMA.COLUMNS col
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND col.TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND col.TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(sequenceName) */
  AND col.COLUMN_NAME IN /*sequenceName*/('%')
  /*end*/
ORDER BY col.TABLE_SCHEMA, col.TABLE_NAME, col.ORDINAL_POSITION
