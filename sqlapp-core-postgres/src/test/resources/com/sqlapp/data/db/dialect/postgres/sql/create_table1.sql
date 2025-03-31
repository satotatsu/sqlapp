CREATE UNLOGGED TABLE "tableA"
(
	  cola INT
	, colb BIGINT
	, colc VARCHAR(50)
	, cold TIMESTAMP(0)
	, cole enum('a', 'b', 'c')
	, colf BOOLEAN
	, colg CHAR(15)
	, colh CHAR(30)
	, CONSTRAINT exc1 EXCLUDE USING GIST ( colb WITH && )
)