SELECT procid AS routine_id, paramid AS parameter_id, paramtype AS parameter_type,
       paramxid AS extended_type_id, paramattr AS parameter_mode
FROM sysproccolumns
WHERE procid IN /*routineIds*/(0)
ORDER BY procid, paramid
