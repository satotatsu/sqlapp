SELECT
    rc.RDB$CONSTRAINT_NAME AS CONSTRAINT_NAME
  , rc.RDB$CONSTRAINT_TYPE AS CONSTRAINT_TYPE
  , rc.RDB$RELATION_NAME AS TABLE_NAME
  , rc.RDB$DEFERRABLE AS DEFERRABLE
  , rc.RDB$INITIALLY_DEFERRED AS INITIALLY_DEFERRED
--  , i.RDB$UNIQUE_FLAG AS NON_UNIQUE
  , i.RDB$INDEX_NAME AS INDEX_NAME
  , iss.RDB$FIELD_NAME AS COLUMN_NAME
  , i.RDB$INDEX_TYPE AS IS_DESC
  , i.RDB$INDEX_INACTIVE AS INDEX_INACTIVE
FROM RDB$RELATION_CONSTRAINTS rc
INNER JOIN RDB$INDICES i
  ON (rc.RDB$INDEX_NAME = i.RDB$INDEX_NAME)
INNER JOIN RDB$INDEX_SEGMENTS iss
  ON (i.RDB$INDEX_NAME = iss.RDB$INDEX_NAME)
WHERE RC.RDB$CONSTRAINT_TYPE IN ('PRIMARY KEY', 'UNIQUE')
  /*if isNotEmpty(constraintName)*/
  AND rc.RDB$CONSTRAINT_NAME IN /*constraintName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND rc.RDB$RELATION_NAME IN /*tableName*/('%')
  /*end*/
  /*if readerOptions.excludeSystemObjects */
  AND i.RDB$SYSTEM_FLAG=0
  /*end*/
ORDER BY rc.RDB$CONSTRAINT_NAME, rc.RDB$RELATION_NAME, iss.RDB$FIELD_POSITION