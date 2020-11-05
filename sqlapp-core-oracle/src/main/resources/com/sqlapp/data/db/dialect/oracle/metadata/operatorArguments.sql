SELECT
  o.*
FROM all_oparguments o
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND o.OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(operatorName)*/
  AND o.OPERATOR_NAME IN /*operatorName*/('%')
  /*end*/
ORDER BY o.OWNER, o.OPERATOR_NAME, o.BINDING#, o.POSITION
