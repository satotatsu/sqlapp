INSERT INTO `tableA` ( cola, colb, colc, cold )
VALUES (1, 'bvalue', '2016-01-12 12:32:30', 1 )
ON DUPLICATE KEY UPDATE colb=VALUES( colb ), colc=VALUES( colc ), cold=VALUES( cold )