CREATE TABLE IF NOT EXISTS "tableA"
(
	  "cola" INTEGER
	, "colb" BIGINT
	, "colc" VARCHAR(50)
	, "cold" TIMESTAMP(0)
	, INDEX "indexa" ( "colc" )
) COMMENT='comment!!!'
PARTITION BY RANGE( "cola" )
(
	PARTITION "p0" STARTING FROM (0) ENDING AT (1) IN "table_space0"
	, PARTITION "p2" STARTING FROM (MAXVALUE) ENDING AT (MAXVALUE) IN "table_space2"
)