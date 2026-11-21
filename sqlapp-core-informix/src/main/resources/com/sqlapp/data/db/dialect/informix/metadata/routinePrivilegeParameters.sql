SELECT p.procid AS routine_id, p.paramid AS parameter_id, p.paramtype AS parameter_type,
       p.paramxid AS extended_type_id, p.paramattr AS parameter_mode, p.paramlen AS parameter_length,
       x.owner AS type_owner, x.name AS type_name, x.mode AS type_kind
FROM sysproccolumns p
LEFT JOIN sysxtdtypes x ON x.extended_id = p.paramxid
WHERE p.procid IN /*routineIds*/(0)
ORDER BY p.procid, p.paramid
