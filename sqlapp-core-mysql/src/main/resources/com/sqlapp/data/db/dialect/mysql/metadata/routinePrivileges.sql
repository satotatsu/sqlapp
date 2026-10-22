SELECT p.Host, p.User, p.Db, p.Routine_name, p.Routine_type, p.Proc_priv
FROM mysql.procs_priv p
WHERE 1=1
  /*if isNotEmpty(objectName)*/
  AND p.Routine_name IN /*objectName*/('%')
  /*end*/
ORDER BY p.Db, p.Routine_name, p.Routine_type, p.User, p.Host
