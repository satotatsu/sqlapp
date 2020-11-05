DELETE FROM "tableA"
WHERE 1=1
	AND "colA" = /*colA*/0
	AND "colB" = /*colB*/0
	AND "lock_version" = COALESCE( /*lock_version*/0, "lock_version", 0 )
