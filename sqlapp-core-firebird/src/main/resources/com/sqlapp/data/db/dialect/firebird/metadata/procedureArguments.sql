SELECT
pp.RDB$PARAMETER_NAME AS PARAMETER_NAME
, pp.RDB$PROCEDURE_NAME AS ROUTINE_NAME
, pp.RDB$PARAMETER_NUMBER
, pp.RDB$PARAMETER_TYPE AS PARAMETER_TYPE
, pp.RDB$FIELD_SOURCE AS FIELD_SOURCE
, pp.RDB$DESCRIPTION AS REMARKS
, pp.RDB$SYSTEM_FLAG AS SYSTEM_FLAG
, pp.RDB$DEFAULT_VALUE AS DEFAULT_VALUE
, pp.RDB$DEFAULT_SOURCE AS DEFAULT_SOURCE
, pp.RDB$COLLATION_ID
, pp.RDB$NULL_FLAG AS NULL_FLAG
, pp.RDB$PARAMETER_MECHANISM AS PARAMETER_MECHANISM
, pp.RDB$FIELD_NAME AS FIELD_NAME
, pp.RDB$RELATION_NAME AS RELATION_NAME
, pp.RDB$DB_KEY
, c.RDB$CHARACTER_SET_NAME AS CHARACTER_SET_NAME
, c.RDB$DEFAULT_COLLATE_NAME AS COLLATION_NAME
, f.RDB$FIELD_TYPE AS FIELD_TYPE
, f.RDB$FIELD_SUB_TYPE AS FIELD_SUB_TYPE
, f.RDB$FIELD_LENGTH AS FIELD_LENGTH
, f.RDB$FIELD_PRECISION AS FIELD_PRECISION
, f.RDB$FIELD_SCALE AS FIELD_SCALE
, f.RDB$SEGMENT_LENGTH AS SEGMENT_LENGTH
, fd.RDB$LOWER_BOUND AS LOWER_BOUND
, fd.RDB$UPPER_BOUND AS UPPER_BOUND
FROM RDB$PROCEDURE_PARAMETERS pp
INNER JOIN RDB$FIELDS f
  ON (pp.RDB$FIELD_SOURCE=f.RDB$FIELD_NAME)
LEFT OUTER JOIN RDB$FIELD_DIMENSIONS fd
  ON (pp.RDB$FIELD_SOURCE=fd.RDB$FIELD_NAME)
LEFT OUTER JOIN RDB$COLLATIONS col
  ON (pp.RDB$COLLATION_ID = col.RDB$COLLATION_ID
      AND
      pp.RDB$PROCEDURE_NAME = col.RDB$FUNCTION_NAME)
LEFT OUTER JOIN RDB$CHARACTER_SETS c
  ON (col.RDB$CHARACTER_SET_ID = c.RDB$CHARACTER_SET_ID
      AND
      col.RDB$FUNCTION_NAME = c.RDB$FUNCTION_NAME)
WHERE 1=1
  /*if isNotEmpty(routineName) */
  AND pp.RDB$PROCEDURE_NAME IN /*routineName*/('%')
  /*end*/
ORDER BY pp.RDB$PROCEDURE_NAME, pp.RDB$PARAMETER_TYPE, pp.RDB$PARAMETER_NUMBER
