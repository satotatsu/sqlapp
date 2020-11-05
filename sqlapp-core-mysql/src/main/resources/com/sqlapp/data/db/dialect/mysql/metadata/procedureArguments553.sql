SELECT
p.*
FROM information_schema.parameters p
WHERE p.routine_type='PROCEDURE'
  /*if isNotEmpty(schemaName) */
  AND p.specific_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(routineName) */
  AND p.specific_name IN /*routineName*/('%')
  /*end*/
order by specific_schema, specific_name, ordinal_position