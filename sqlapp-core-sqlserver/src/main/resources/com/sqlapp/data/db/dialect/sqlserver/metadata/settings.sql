SELECT a.*
FROM
(
	SELECT 'Collation' AS name, CAST(DATABASEPROPERTYEX ( DB_NAME(), 'Collation' ) AS NVARCHAR) AS value
	UNION ALL
	SELECT 'ComparisonStyle' AS name, CAST(DATABASEPROPERTYEX ( DB_NAME(), 'ComparisonStyle' ) AS NVARCHAR) AS value
	UNION ALL
	SELECT 'IsAnsiNullDefault' AS name, CAST(DATABASEPROPERTY ( DB_NAME(), 'IsAnsiNullDefault' ) AS NVARCHAR) AS value
	UNION ALL
	SELECT 'IsAnsiNullsEnabled' AS name, CAST(DATABASEPROPERTY ( DB_NAME(), 'IsAnsiNullsEnabled' ) AS NVARCHAR) AS value
	UNION ALL
	SELECT 'IsAutoShrink' AS name, CAST(DATABASEPROPERTY ( DB_NAME(), 'IsAutoShrink' ) AS NVARCHAR) AS value
	UNION ALL
	SELECT 'IsAutoCreateStatistics' AS name, CAST(DATABASEPROPERTY ( DB_NAME(), 'IsAutoCreateStatistics' ) AS NVARCHAR) AS value
	UNION ALL
	SELECT 'IsAutoUpdateStatistics' AS name, CAST(DATABASEPROPERTY ( DB_NAME(), 'IsAutoUpdateStatistics' ) AS NVARCHAR) AS value
	UNION ALL
	SELECT 'IsFulltextEnabled' AS name, CAST(DATABASEPROPERTY ( DB_NAME(), 'IsFulltextEnabled' ) AS NVARCHAR) AS value
	UNION ALL
	SELECT 'IsInStandBy' AS name, CAST(DATABASEPROPERTY ( DB_NAME(), 'IsInStandBy' ) AS NVARCHAR) AS value
	UNION ALL
	SELECT 'SQLSortOrder' AS name, CAST(DATABASEPROPERTYEX ( DB_NAME(), 'SQLSortOrder' ) AS NVARCHAR) AS value
--	UNION ALL
--	SELECT 'Status' AS setting_name, CAST(DATABASEPROPERTY ( DB_NAME(), 'Status' ) AS NVARCHAR) AS value
) a
WHERE 1=1
  /*if isNotEmpty(settingName) */
  AND a.name IN /*settingName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY a.name