WITH tmp_constraints AS
(
    SELECT *
    FROM all_constraints c
    WHERE c.CONSTRAINT_TYPE IN ('R', 'P', 'U')
	/*if isNotEmpty(schemaName)*/
	AND c.OWNER IN /*schemaName*/('%')
	/*end*/
	/*if isNotEmpty(tableName)*/
	AND c.TABLE_NAME IN /*tableName*/('%')
	/*end*/
)
, tmp_cons_names AS
(
    SELECT OWNER
    , CONSTRAINT_NAME
    FROM tmp_constraints c
    GROUP BY OWNER, CONSTRAINT_NAME
    UNION
    SELECT R_OWNER AS OWNER
    , R_CONSTRAINT_NAME AS CONSTRAINT_NAME
    FROM tmp_constraints c
    GROUP BY R_OWNER, R_CONSTRAINT_NAME
)
, tmp_cons_columns AS
(
    SELECT *
    FROM all_cons_columns cc
    WHERE EXISTS
      (
          SELECT 1
          FROM tmp_cons_names c
          WHERE cc.OWNER=c.OWNER
            AND cc.CONSTRAINT_NAME=c.CONSTRAINT_NAME
      )
)
SELECT
  c.OWNER
, c.CONSTRAINT_NAME
, c.TABLE_NAME
, rc.TABLE_NAME AS REFERENCE_TABLE_NAME
, cc.COLUMN_NAME
, cc.POSITION
, rcc.COLUMN_NAME AS REFERENCE_COLUMN_NAME
, c.CONSTRAINT_TYPE
, c.SEARCH_CONDITION
, c.R_OWNER AS REFERENCE_OWNER
, c.R_CONSTRAINT_NAME AS REFERENCE_CONSTRAINT_NAME
, c.GENERATED
, C.DEFERRABLE
, C.DEFERRED
, C.LAST_CHANGE
, C.STATUS   --ENABLED OR DISABLED
, C.INVALID  --INVALID OR NULLL
, C.DELETE_RULE
FROM tmp_constraints c
INNER JOIN tmp_cons_columns cc
ON (
     c.OWNER=cc.OWNER
     AND
     c.CONSTRAINT_NAME=cc.CONSTRAINT_NAME
    )
INNER JOIN tmp_constraints rc
ON (
     c.R_OWNER=rc.OWNER
     AND
     c.R_CONSTRAINT_NAME=rc.CONSTRAINT_NAME
    )
INNER JOIN tmp_cons_columns rcc
ON (
     c.R_OWNER=rcc.OWNER
     AND
     c.R_CONSTRAINT_NAME=rcc.CONSTRAINT_NAME
     AND
     cc.POSITION=rcc.POSITION
    )
WHERE 1=1
ORDER BY c.OWNER, c.CONSTRAINT_NAME, c.TABLE_NAME, cc.POSITION
