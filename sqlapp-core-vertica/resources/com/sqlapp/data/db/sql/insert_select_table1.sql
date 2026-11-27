INSERT INTO TABLE "tableA"
(
	  "colA"
	, "colB"
	, "colC"
	, "lock_version"
)
VALUES
SELECT "colA", "colB", "colC", "lock_version"
FROM "tableA" WHERE
NOT EXISTS (
	SELECT 1
	FROM "tableA"
	WHERE 1=1
	AND "colA" = /*colA*/0
	AND "colB" = /*colB*/0
)