SELECT
  h.OWNER
, h.DIMENSION_NAME
, h.HIERARCHY_NAME
, h.POSITION
, h.CHILD_LEVEL_NAME
, h.JOIN_KEY_ID
, h.PARENT_LEVEL_NAME
FROM ALL_DIM_CHILD_OF h
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND h.OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(dimensionName)*/
  AND h.DIMENSION_NAME IN /*dimensionName*/('%')
  /*end*/
ORDER BY h.OWNER, h.DIMENSION_NAME, h.HIERARCHY_NAME, h.POSITION
