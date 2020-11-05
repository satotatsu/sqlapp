MERGE tableA
USING (
	SELECT 1 AS cola, 'bvalue' AS colb, {ts '2016-01-12 12:32:30'} AS colc
	UNION ALL
	SELECT 2 AS cola, 'value2' AS colb, {ts '2017-01-15 14:32:30'} AS colc
) AS _target
ON tableA.cola=_target.cola
WHEN MATCHED THEN
	UPDATE SET colb=COALESCE( colb, _target.colb ) AND colc=_target.colc
WHEN NOT MATCHED THEN
	INSERT ( cola, colb, colc ) VALUES ( _target.cola, _target.colb, _target.colc )