INSERT INTO "tableA" /*テーブル名_tableA*/
(
	cola /*カラムA*/
	, colb /*カラムB*/
	, colc 
)
VALUES
(
	1
	, 'bvalue'
	, TIMESTAMP '2016-01-12 12:32:30' 
)
ON CONFLICT ON CONSTRAINT "PK_tableA"
DO UPDATE
	SET colb /*カラムB*/ = 'bvalue'
	, colc = TIMESTAMP '2016-01-12 12:32:30'