SELECT
  i.INDEX_ID
  , i.INDEX_NAME
  , i.INDEX_SCHEMA_NAME
  , i.SOURCE_TABLE_NAME
  , i.SOURCE_TABLE_SCHEMA_NAME
  , i.TOKENIZER_NAME
  , i.TOKENIZER_SCHEMA_NAME
  , i.STEMMER_NAME
  , i.STEMMER_SCHEMA_NAME
  , i.TEXT_COL
FROM V_CATALOG.TEXT_INDICES i
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND i.SOURCE_TABLE_SCHEMA_NAME IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND i.SOURCE_TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(indexName)*/
  AND i.INDEX_NAME IN /*indexName*/('%')
  /*end*/
ORDER BY i.SOURCE_TABLE_SCHEMA_NAME, i.SOURCE_TABLE_NAME, i.INDEX_NAME
