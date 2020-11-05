SELECT
	I.*
	, IC.*
FROM INDEXES I
LEFT OUTER JOIN INDEX_COLUMNS IC
ON (
	I.SCHEMA_NAME=IC.SCHEMA_NAME
	AND
	I.INDEX_NAME=IC.INDEX_NAME
	AND
	I.TABLE_NAME=IC.TABLE_NAME
)
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND I.SCHEMA_NAME IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND I.TABLE_NAME IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(indexName)*/
  AND I.INDEX_NAME IN /*indexName;type=NVARCHAR*/('%')
  /*end*/
  AND CONSTRAINT IS NULL
ORDER BY I.SCHEMA_NAME, I.TABLE_NAME, I.INDEX_NAME, IC.POSITION
