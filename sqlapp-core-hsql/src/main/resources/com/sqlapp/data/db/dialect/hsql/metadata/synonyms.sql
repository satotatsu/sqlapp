SELECT * FROM INFORMATION_SCHEMA.SYSTEM_SYNONYMS
WHERE TRUE
  /*if isNotEmpty(catalogName)*/
  AND SYNONYM_CATALOG IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND SYNONYM_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(synonymName)*/
  AND SYNONYM_NAME IN /*synonymName*/('%')
  /*end*/
ORDER BY SYNONYM_CATALOG, SYNONYM_SCHEMA, SYNONYM_NAME
