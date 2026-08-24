SELECT
  q.SEQUENCENAME AS sequence_name
, s.SCHEMANAME AS schema_name
, q.CURRENTVALUE
, q.STARTVALUE
, q.MINIMUMVALUE
, q.MAXIMUMVALUE
, q.INCREMENT
, q.CYCLEOPTION
FROM SYS.SYSSEQUENCES q
INNER JOIN SYS.SYSSCHEMAS s
  ON (q.SCHEMAID=s.SCHEMAID)
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND s.SCHEMANAME IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(sequenceName)*/
  AND q.SEQUENCENAME IN /*sequenceName*/('%')
  /*end*/
ORDER BY s.SCHEMANAME, q.SEQUENCENAME
