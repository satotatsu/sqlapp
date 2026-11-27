SELECT p.procid AS routine_id, TRIM(p.owner) AS routine_schema, p.procname AS routine_name
FROM sysprocedures p
WHERE p.procid IN /*routineIds*/(0)
ORDER BY p.procid
