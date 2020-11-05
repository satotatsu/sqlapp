SELECT 
  p.*
, r.routine_name
FROM information_schema.parameters p
INNER JOIN information_schema.routines r
  ON (
	  routine_type='PROCEDURE'
	  AND r.specific_catalog= p.specific_catalog
	  AND r.specific_schema= p.specific_schema
	  AND r.specific_name= p.specific_name
  )
WHERE 1=1
  AND p.IS_RESULT='NO'
  /*if isNotEmpty(catalogName) */
  AND p.specific_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName) */
  AND p.specific_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(routineName) */
  AND p.specific_name IN /*routineName*/('%')
  /*end*/
ORDER BY p.specific_catalog, p.specific_schema, p.specific_name, p.ordinal_position