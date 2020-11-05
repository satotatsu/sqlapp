SELECT
p.*
FROM information_schema.parameters p
WHERE p.routine_type='FUNCTION'
  /*if isNotEmpty(schemaName) */
  AND p.specific_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(routineName) */
  AND p.specific_name IN /*routineName*/('%')
  /*end*/
  AND ordinal_position>0
ORDER BY specific_schema, specific_name, ordinal_position