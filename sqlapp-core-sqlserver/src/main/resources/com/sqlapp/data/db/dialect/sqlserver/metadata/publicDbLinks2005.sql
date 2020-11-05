SELECT
  DB_NAME() AS catalog_name
, SS.name
, SS.product
, SS.provider
, SS.provider_string
, SS.data_source
, SS.catalog
, SS.modify_date
, SR.remote_name AS user_name
FROM sys.servers SS
LEFT OUTER JOIN sys.linked_logins SR
  on (SS.server_id=SR.server_id)
WHERE 1=1
  /*if isNotEmpty(dbLinkName)*/
  AND SS.name IN /*dbLinkName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY SS.name