SELECT opclassid AS operator_class_id, opclassname AS operator_class_name,
       TRIM(owner) AS operator_class_owner, amid AS access_method
FROM sysopclasses
WHERE opclassid IN /*operatorClassIds*/(0)
ORDER BY opclassid
