MERGE tableA
USING
(
	SELECT 
	${colA} AS col_a
	, ${colB} AS col_b
	, ${createdAt} AS created_at
	, ${updatedAt} AS updated_at
	, 0 AS lock_version
) AS _target
ON
(
	tableA.col_a=_target.col_a
)
WHEN MATCHED THEN
	UPDATE SET
		col_b=COALESCE( col_b, _target.col_b )
		, updated_at=COALESCE( updated_at, _target.updated_at )
		, lock_version=COALESCE( lock_version, 0 ) + 1
WHEN NOT MATCHED THEN
	INSERT
	(
		col_a
		, col_b
		, created_at
		, updated_at
		, lock_version
	)
	VALUES
	(
		_target.col_a
		, _target.col_b
		, _target.created_at
		, _target.updated_at
		, _target.lock_version
	)