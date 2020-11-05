INSERT INTO `tableA` ( cola, colb, colc, lock_version )
VALUES (1, 'bvalue', '2016-01-12 12:32:30', 1 )
ON DUPLICATE KEY UPDATE colb=VALUES( colb ), colc=COALESCE( colc, VALUES( colc )), lock_version=lock_version + 1