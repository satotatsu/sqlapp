SELECT
  DB_NAME() AS catalog_name
, ss.srvname AS name
, ss.srvproduct AS product
, ss.providername AS provider
, ss.providerstring AS provider_string
, ss.datasource AS data_source
, ss.catalog
, ss.schemadate AS modify_date
, sr.rmtloginame AS user_name
FROM sysservers SS
LEFT OUTER JOIN sysoledbusers sr
  on (ss.srvid=sr.rmtsrvid)
WHERE 1=1
  /*if isNotEmpty(dbLinkName)*/
  AND ss.srvname IN /*dbLinkName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY ss.srvname