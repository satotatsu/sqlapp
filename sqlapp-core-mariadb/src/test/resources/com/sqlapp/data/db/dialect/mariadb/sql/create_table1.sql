CREATE TABLE IF NOT EXISTS `tableA`
(
	  cola INT
	, colb BIGINT
	, colc VARCHAR(50) CHARACTER SET utf8 COLLATE utf8mb4_binary
	, cold DATETIME(0)
	, cole ENUM('a', 'b', 'c')
	, INDEX indexa USING HASH ( colc )
) ENGINE =innodb COMMENT ='comment!!!'
PARTITION BY RANGE( cola )
SUBPARTITION BY KEY( colb )
(
	PARTITION p0 VALUES LESS THAN MAXVALUE TABLESPACE = table_space0 COMMENT ='p0 partition'
	(
		SUBPARTITION s0 TABLESPACE = table_space0 COMMENT ='s0 partition'
		, SUBPARTITION s1 TABLESPACE = table_space1 COMMENT ='s1 partition'
		, SUBPARTITION s2 TABLESPACE = table_space2 COMMENT ='s2 partition'
	)
)