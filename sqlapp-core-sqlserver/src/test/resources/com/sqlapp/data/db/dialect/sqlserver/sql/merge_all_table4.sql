MERGE tableA AS _target_
USING tableA_temp AS _source_
ON(
	_target_.cola=_source_.cola
)
WHEN MATCHED
	THEN UPDATE
		SET _target_.colb=_source_.colb
		, _target_.updated_at=/*update_updated_at*/
		, _target_.lock_version=_source_.lock_version
WHEN NOT MATCHED BY TARGET
	THEN INSERT
	(
		colb
		, created_at
		, updated_at
		, lock_version
	)
	VALUES
	(
		_source_.colb
		, COALESCE(/*insert_created_at*/, CURRENT_TIMESTAMP )
		, _source_.updated_at
		, _source_.lock_version
	)
WHEN NOT MATCHED BY SOURCE
	THEN DELETE;