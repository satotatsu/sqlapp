SELECT
  DB_NAME() AS catalog_name
, ss.name
, ss.product
, ss.provider
, ss.provider_string
, ss.data_source
, ss.catalog
, ss.modify_date
, sr.remote_name AS user_name
FROM sys.servers ss
LEFT OUTER JOIN sys.linked_logins sr
  on (ss.server_id=SR.server_id)
WHERE 1=1
  /*if isNotEmpty(dbLinkName)*/
  AND ss.name IN /*dbLinkName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY ss.name