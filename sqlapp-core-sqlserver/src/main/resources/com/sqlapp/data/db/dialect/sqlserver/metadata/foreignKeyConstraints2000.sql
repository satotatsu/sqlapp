SELECT 
  DB_NAME() AS catalog_name
, su.name AS schema_name
, OBJECT_NAME(fk.constid) AS constraint_name
, OBJECT_NAME(fk.fkeyid) AS table_name
, OBJECT_NAME(fk.rkeyid) AS referential_table_name
, COL_NAME(fk.fkeyid, fk.fkey) AS column_name
, COL_NAME(fk.rkeyid, fk.rkey) AS referential_column_name
, OBJECTPROPERTY(fk.constid, 'CnstIsUpdateCascade')  --ON UPDATE CASCADE オプション
  AS IsUpdateCascade
, OBJECTPROPERTY(fk.constid, 'CnstIsDeleteCascade')  --ON DELETE CASCADE オプション
  AS IsDeleteCascade
, OBJECTPROPERTY(fk.constid, 'CnstIsDisabled')  --IsDisabled
  AS IsDisabled
FROM sysforeignkeys fk 
INNER JOIN sysobjects so
  ON (fk.fkeyid=so.id)
INNER JOIN sysusers su
  ON (so.uid = su.uid)
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND su.name IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND OBJECT_NAME(fk.fkeyid) IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY su.name, OBJECT_NAME(fk.fkeyid), OBJECT_NAME(fk.constid), fk.fkey
