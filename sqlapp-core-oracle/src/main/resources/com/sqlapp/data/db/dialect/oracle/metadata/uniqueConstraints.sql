SELECT
  c.OWNER
, c.CONSTRAINT_NAME
, c.TABLE_NAME
, cc.COLUMN_NAME
, c.CONSTRAINT_TYPE
, c.GENERATED
, C.DEFERRABLE
, C.DEFERRED
, C.LAST_CHANGE
, C.STATUS   --ENABLED OR DISABLED
, C.INVALID  --INVALID OR NULLL
, C.VALIDATED
, C.RELY
FROM all_constraints c
INNER JOIN all_cons_columns cc
ON (
     c.OWNER=cc.OWNER
     AND
     c.CONSTRAINT_NAME=cc.CONSTRAINT_NAME
    )
WHERE c.CONSTRAINT_TYPE IN ('P','U')
  AND NOT (c.CONSTRAINT_TYPE='U' AND c.GENERATED='GENERATED NAME' AND EXISTS (
    SELECT 1
    FROM ALL_IND_COLUMNS nic
    INNER JOIN ALL_NESTED_TABLES nt
      ON nt.OWNER=nic.TABLE_OWNER AND nt.PARENT_TABLE_NAME=nic.TABLE_NAME
      AND nt.PARENT_TABLE_COLUMN=nic.COLUMN_NAME
    WHERE nic.INDEX_OWNER=c.INDEX_OWNER AND nic.INDEX_NAME=c.INDEX_NAME
  ))
  /*if isNotEmpty(schemaName)*/
  AND c.OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND c.TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND c.CONSTRAINT_NAME IN /*constraintName*/('%')
  /*end*/
ORDER BY c.OWNER, c.TABLE_NAME, c.CONSTRAINT_TYPE, c.CONSTRAINT_NAME, cc.POSITION
