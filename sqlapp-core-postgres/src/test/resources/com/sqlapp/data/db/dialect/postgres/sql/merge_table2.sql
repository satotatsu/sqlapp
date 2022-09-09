INSERT INTO "tableA" /*テーブル名_tableA*/
(
	cola /*カラムA*/
	, colb /*カラムB*/
	, created_at
	, updated_at
	, lock_version 
)
VALUES
(
	
	/*cola*/0
	, /*colb*/''
	, CURRENT_TIMESTAMP
	, CURRENT_TIMESTAMP
	, 0 
)
ON CONFLICT ON CONSTRAINT "PK_tableA"
DO UPDATE
	SET colb /*カラムB*/ = /*colb*/''
	, updated_at = CURRENT_TIMESTAMP
	, lock_version = EXCLUDED.lock_version + 1