SELECT d.*
, coalesce(d.character_maximum_length, d.numeric_precision, d.interval_precision) as length
, dc.is_deferrable
, dc.initially_deferred
FROM information_schema.domains d
LEFT OUTER JOIN information_schema.domain_constraints dc
  ON (d.domain_catalog=dc.domain_catalog
  AND d.domain_schema=dc.domain_schema
  AND d.domain_name=dc.domain_name)
WHERE 1=1
  /*if isNotEmpty(catalogName)*/
  AND d.domain_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND d.domain_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(domainName)*/
  AND d.domain_name IN /*domainName*/('%')
  /*end*/
ORDER BY domain_catalog, domain_schema, domain_name
