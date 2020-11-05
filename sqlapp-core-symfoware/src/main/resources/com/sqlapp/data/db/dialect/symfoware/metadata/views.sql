SELECT
  V.*
FROM SYSCAT.VIEWS V
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND V.VIEWSCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND V.VIEWNAME IN /*tableName*/('%')
  /*end*/
ORDER BY V.VIEWSCHEMA, V.VIEWNAME