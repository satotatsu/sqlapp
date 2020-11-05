SELECT
*
FROM information_schema.domains
WHERE TRUE
  /*if isNotEmpty(catalogName)*/
  AND domain_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND domain_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(domainName)*/
  AND domain_name IN /*domainName*/('%')
  /*end*/
ORDER BY domain_catalog, domain_schema, domain_name
