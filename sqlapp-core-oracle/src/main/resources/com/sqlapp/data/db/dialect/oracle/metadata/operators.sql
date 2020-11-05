SELECT
  o.*
  , ob.BINDING#
  , ob.FUNCTION_NAME
  , ob.RETURN_SCHEMA
  , ob.RETURN_TYPE
  , ob.IMPLEMENTATION_TYPE_SCHEMA --バインディングの戻り型がオブジェクト型である場合、その戻り型のスキーマ名
  , ob.IMPLEMENTATION_TYPE 
  , ob.PROPERTY --演算子バインディングのプロパティ: 
-- ・WITH INDEX CONTEXT 
-- ・COMPUTE ANCILLARY DATA 
-- ・ANCILLARY TO 
-- ・WITH COLUMN CONTEXT 
-- ・WITH INDEX, COLUMN CONTEXT 
-- ・COMPUTE ANCILLARY DATA, WITH COLUMN CONTEXT  
   , oc.COMMENTS
FROM all_operators o
INNER JOIN all_opbindings ob
ON (
     o.OWNER=ob.OWNER
     AND
     o.OPERATOR_NAME=ob.OPERATOR_NAME
    )
LEFT OUTER JOIN all_operator_comments oc
ON (
     o.OWNER=oc.OWNER
     AND
     o.OPERATOR_NAME=oc.OPERATOR_NAME
    )
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND o.OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(operatorName)*/
  AND o.OPERATOR_NAME IN /*operatorName*/('%')
  /*end*/
ORDER BY o.OWNER, o.OPERATOR_NAME, ob.BINDING#
