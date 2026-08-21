SELECT
  DB_NAME() AS catalog_name
, SS.srvname AS name
, SS.srvnetname AS data_source
, SS.srvclass
, SS.srvstatus
, SS.srvsecmech
FROM master.dbo.sysservers SS
WHERE 1=1
  /*if isNotEmpty(dbLinkName)*/
  AND SS.srvname IN /*dbLinkName;type=VARCHAR*/('%')
  /*end*/
ORDER BY SS.srvname
