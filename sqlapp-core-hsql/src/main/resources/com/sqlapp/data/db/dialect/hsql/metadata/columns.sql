SELECT COL.*
 , SEQ_USAGE.SEQUENCE_NAME   -- 紐づいているシーケンス名
 , SEQ.DATA_TYPE AS SEQUENCE_DATA_TYPE             -- シーケンスのデータ型
 , SEQ.START_WITH            -- 開始値
 , SEQ.INCREMENT             -- 増分ステップ (提示された定義なら 3)
 , SEQ.MINIMUM_VALUE         -- ★ シーケンスの最小値
 , SEQ.MAXIMUM_VALUE         -- ★ シーケンスの最大値
 , SEQ.NEXT_VALUE            -- 次に発行される予定の値
, C.COMMENT
FROM information_schema.columns COL
LEFT OUTER JOIN INFORMATION_SCHEMA.SYSTEM_COLUMN_SEQUENCE_USAGE SEQ_USAGE
  ON (
    COL.TABLE_CATALOG = SEQ_USAGE.TABLE_CATALOG
    AND COL.TABLE_SCHEMA = SEQ_USAGE.TABLE_SCHEMA
    AND COL.TABLE_NAME = SEQ_USAGE.TABLE_NAME
    AND COL.COLUMN_NAME = SEQ_USAGE.COLUMN_NAME
  )
-- 2. シーケンスの具体的な詳細メタデータを結合
LEFT OUTER JOIN INFORMATION_SCHEMA.SEQUENCES SEQ
  ON (
    SEQ_USAGE.SEQUENCE_CATALOG = SEQ.SEQUENCE_CATALOG
    AND SEQ_USAGE.SEQUENCE_SCHEMA = SEQ.SEQUENCE_SCHEMA
    AND SEQ_USAGE.SEQUENCE_NAME = SEQ.SEQUENCE_NAME
  )
LEFT OUTER JOIN INFORMATION_SCHEMA.SYSTEM_COMMENTS C
  ON (
    COL.TABLE_CATALOG=C.OBJECT_CATALOG
    AND COL.TABLE_SCHEMA=C.OBJECT_SCHEMA
    AND COL.TABLE_NAME=C.OBJECT_NAME
    AND COL.COLUMN_NAME=C.COLUMN_NAME
  )
WHERE 1=1
  /*if isNotEmpty(catalogName)*/
  AND table_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND table_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND table_name IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(columnName)*/
  AND column_name IN /*columnName*/('%')
  /*end*/
ORDER BY table_catalog, table_schema, table_name, ordinal_position
