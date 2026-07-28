SELECT
    SEQUENCE_CATALOG
  , SEQUENCE_SCHEMA
  , SEQUENCE_NAME
  , DATA_TYPE
  , NUMERIC_PRECISION
  , NUMERIC_SCALE
  , START_VALUE
  , MINIMUM_VALUE
  , MAXIMUM_VALUE
  , INCREMENT
  , CYCLE_OPTION
FROM information_schema.SEQUENCES
WHERE 1=1
  /*if isNotEmpty(catalogName)*/
  AND SEQUENCE_CATALOG IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND SEQUENCE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(sequenceName)*/
  AND SEQUENCE_NAME IN /*sequenceName*/('%')
  /*end*/
ORDER BY SEQUENCE_SCHEMA, SEQUENCE_NAME
