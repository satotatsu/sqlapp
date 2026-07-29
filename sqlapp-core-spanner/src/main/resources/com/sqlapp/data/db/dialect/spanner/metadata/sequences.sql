SELECT
  seq.*
, (
    SELECT opt.OPTION_VALUE
    FROM INFORMATION_SCHEMA.SEQUENCE_OPTIONS opt
    WHERE opt.CATALOG=seq.CATALOG
      AND opt.SCHEMA=seq.SCHEMA
      AND opt.NAME=seq.NAME
      AND opt.OPTION_NAME='start_with_counter'
  ) AS START_WITH_COUNTER
, (
    SELECT opt.OPTION_VALUE
    FROM INFORMATION_SCHEMA.SEQUENCE_OPTIONS opt
    WHERE opt.CATALOG=seq.CATALOG
      AND opt.SCHEMA=seq.SCHEMA
      AND opt.NAME=seq.NAME
      AND opt.OPTION_NAME='skip_range_min'
  ) AS SKIP_RANGE_MIN
, (
    SELECT opt.OPTION_VALUE
    FROM INFORMATION_SCHEMA.SEQUENCE_OPTIONS opt
    WHERE opt.CATALOG=seq.CATALOG
      AND opt.SCHEMA=seq.SCHEMA
      AND opt.NAME=seq.NAME
      AND opt.OPTION_NAME='skip_range_max'
  ) AS SKIP_RANGE_MAX
FROM INFORMATION_SCHEMA.SEQUENCES seq
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND seq.SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(sequenceName) */
  AND seq.NAME IN /*sequenceName*/('%')
  /*end*/
ORDER BY seq.SCHEMA, seq.NAME
