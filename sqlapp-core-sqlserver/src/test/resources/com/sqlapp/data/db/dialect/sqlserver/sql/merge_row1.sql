MERGE tableA
USING
(
	SELECT 1 AS cola, 'bvalue' AS colb, {ts '2016-01-12 12:32:30'} AS colc
)
AS _target
ON tableA.cola = _target.cola
WHEN MATCHED THEN
	UPDATE SET
	colb = _target.colb
	, colc = _target.colc
WHEN NOT MATCHED THEN
	INSERT
	(
		cola
		, colb
		, colc
	)
	VALUES
	(
		_target.cola
		, _target.colb
		, _target.colc
	)
;