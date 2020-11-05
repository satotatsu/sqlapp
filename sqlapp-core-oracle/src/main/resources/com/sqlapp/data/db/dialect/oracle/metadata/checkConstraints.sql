SELECT
  c.OWNER
, c.CONSTRAINT_NAME
, c.TABLE_NAME
, cc.COLUMN_NAME
, c.CONSTRAINT_TYPE
, c.SEARCH_CONDITION
, c.GENERATED
, C.DEFERRABLE
, C.DEFERRED
, C.LAST_CHANGE
, C.STATUS   --ENABLED OR DISABLED
, C.INVALID  --INVALID OR NULLL
FROM all_constraints c
INNER JOIN all_cons_columns cc
ON (
     c.OWNER=cc.OWNER
     AND
     c.CONSTRAINT_NAME=cc.CONSTRAINT_NAME
    )
WHERE c.CONSTRAINT_TYPE='C'
  /*if isNotEmpty(schemaName)*/
  AND c.OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND c.TABLE_NAME IN /*tableName*/('%')
  /*end*/
ORDER BY c.OWNER, c.CONSTRAINT_NAME, c.TABLE_NAME, cc.POSITION
