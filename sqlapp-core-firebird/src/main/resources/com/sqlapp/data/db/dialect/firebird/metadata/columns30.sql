SELECT
RF.RDB$RELATION_NAME AS TABLE_NAME
, RF.RDB$FIELD_NAME AS COLUMN_NAME
, RF.RDB$BASE_FIELD AS BASE_COLUMN_NAME
, F.RDB$FIELD_TYPE AS FIELD_TYPE
, F.RDB$FIELD_SUB_TYPE AS FIELD_SUB_TYPE
, F.RDB$FIELD_LENGTH AS FIELD_LENGTH
, F.RDB$FIELD_PRECISION AS FIELD_PRECISION
, F.RDB$FIELD_SCALE AS FIELD_SCALE
, F.RDB$SEGMENT_LENGTH AS SEGMENT_LENGTH
--, F.RDB$CHARACTER_LENGTH as CHAR_LEN
, RF.RDB$DESCRIPTION AS REMARKS
, RF.RDB$DEFAULT_SOURCE AS DEFAULT_SOURCE
, RF.RDB$FIELD_POSITION AS FIELD_POSITION
, RF.RDB$NULL_FLAG AS NULL_FLAG
, F.RDB$NULL_FLAG AS SOURCE_NULL_FLAG
, F.RDB$VALIDATION_SOURCE AS CHECK_CONDITION
, F.RDB$COMPUTED_SOURCE AS COMPUTED_SOURCE
, FD.RDB$LOWER_BOUND AS LOWER_BOUND
, FD.RDB$UPPER_BOUND AS UPPER_BOUND
, RF.RDB$GENERATOR_NAME AS GENERATOR_NAME
, RF.RDB$IDENTITY_TYPE AS IDENTITY_TYPE
, G.RDB$INITIAL_VALUE AS INITIAL_VALUE
, G.RDB$GENERATOR_INCREMENT AS GENERATOR_INCREMENT
FROM RDB$RELATION_FIELDS RF
INNER JOIN RDB$FIELDS F
  ON (RF.RDB$FIELD_SOURCE=F.RDB$FIELD_NAME)
LEFT OUTER JOIN RDB$FIELD_DIMENSIONS FD
ON (RF.RDB$FIELD_SOURCE=FD.RDB$FIELD_NAME)
LEFT OUTER JOIN RDB$GENERATORS G
ON (RF.RDB$GENERATOR_NAME=G.RDB$GENERATOR_NAME)
WHERE 1=1
AND RF.RDB$RELATION_NAME IN
(
	SELECT R.RDB$RELATION_NAME
	FROM RDB$RELATIONS R
	WHERE 1=1
--	  AND (R.RDB$SYSTEM_FLAG=0 OR R.RDB$SYSTEM_FLAG IS NULL)
	/*if isNotEmpty(tableName) */
	  AND RF.RDB$RELATION_NAME IN /*tableName*/('%')
	/*end*/
)
  /*if readerOptions.excludeSystemObjects */
  AND RDB$RELATION_NAME NOT LIKE 'RDB$%'
  /*end*/
  /*if isNotEmpty(columnName) */
  AND RF.RDB$FIELD_NAME IN /*columnName*/('%')
  /*end*/
ORDER BY RF.RDB$RELATION_NAME, RF.RDB$FIELD_POSITION
