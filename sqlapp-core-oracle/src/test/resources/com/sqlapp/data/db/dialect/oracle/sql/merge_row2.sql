MERGE "tableA"
USING (
	SELECT 1 AS "cola", 'bvalue' AS "colb", 2016-01-12 12:32:30 AS "colc", 1 AS "version_no"
	FROM DUAL
	UNION ALL
	SELECT 2 AS "cola", 'bvalue2' AS "colb", 2017-05-12 12:32:30 AS "colc", 2 AS "version_no"
	FROM DUAL
) AS "_target"
ON "tableA"."cola"="_target"."cola"
WHEN MATCHED THEN
	UPDATE SET "colb"="_target"."colb" AND "colc"="_target"."colc" AND "version_no"="version_no" + 1
WHEN NOT MATCHED THEN
	INSERT ( "cola", "colb", "colc", "version_no" ) VALUES ( "_target"."cola", "_target"."colb", "_target"."colc", "_target"."version_no" )