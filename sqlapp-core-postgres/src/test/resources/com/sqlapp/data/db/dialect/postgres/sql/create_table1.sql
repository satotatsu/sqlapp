CREATE TABLE tableA
(
	  cola INT
	, colb BIGINT
	, colc VARCHAR(50)
	, cold TIMESTAMP(0)
	, cole enum('a', 'b', 'c')
	, CONSTRAINT exc1 EXCLUDE USING GIST ( colb WITH && )
)