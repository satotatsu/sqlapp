SELECT
  DB_NAME() AS catalog_name
, SS.srvname AS name
, SS.srvproduct AS product
, SS.providername AS provider
, SS.providerstring AS provider_string
, SS.datasource AS data_source
, SS.catalog
, SS.schemadate AS modify_date
, SR.rmtloginame AS user_name
FROM sysservers SS
LEFT OUTER JOIN sysoledbusers SR
  on (SS.srvid=SR.rmtsrvid)
WHERE 1=1
  /*if isNotEmpty(dbLinkName)*/
  AND SS.srvname IN /*dbLinkName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY SS.srvname