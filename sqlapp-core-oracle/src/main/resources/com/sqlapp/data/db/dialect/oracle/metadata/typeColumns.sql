SELECT TA.*
FROM ALL_TYPE_ATTRS TA
WHERE 1=1 
  /*if isNotEmpty(schemaName)*/
  AND TA.OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(typeName)*/
  AND TA.TYPE_NAME IN /*typeName*/('%')
  /*end*/
ORDER BY TA.OWNER, TA.TYPE_NAME, TA.ATTR_NO
