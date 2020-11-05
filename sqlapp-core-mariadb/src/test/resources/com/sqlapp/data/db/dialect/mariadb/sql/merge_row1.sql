INSERT INTO `tableA` ( cola, colb, colc )
VALUES (1, 'bvalue', '2016-01-12 12:32:30' )
ON DUPLICATE KEY UPDATE colb=VALUES( colb ), colc=VALUES( colc )