DELETE FROM "tableA"
WHERE 1=1
	AND "col_a" = ${colA}
	AND "col_b" = ${colB}
	AND "lock_version" = COALESCE( ${lockVersion}, "lock_version", 0 )