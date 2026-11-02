SELECT TABLE_CATALOG, TABLE_SCHEMA, TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_TYPE = 'SEQUENCE'
  /*if isNotEmpty(catalogName)*/
  AND TABLE_CATALOG IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(sequenceName)*/
  AND TABLE_NAME IN /*sequenceName*/('%')
  /*end*/
ORDER BY TABLE_SCHEMA, TABLE_NAME
