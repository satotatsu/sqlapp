SELECT
  tc.*
, g.SRS_ID AS SPATIAL_SRS_ID
FROM TABLE_COLUMNS tc
LEFT OUTER JOIN ST_GEOMETRY_COLUMNS g
  ON (g.SCHEMA_NAME = tc.SCHEMA_NAME
  AND g.TABLE_NAME = tc.TABLE_NAME
  AND g.COLUMN_NAME = tc.COLUMN_NAME)
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND tc.SCHEMA_NAME IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND tc.TABLE_NAME IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(columnName)*/
  AND tc.COLUMN_NAME IN /*columnName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY tc.SCHEMA_NAME, tc.TABLE_NAME, tc.POSITION
