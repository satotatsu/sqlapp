INSERT INTO `tableA` ( cola, colb, created_at, updated_at, lock_version )
VALUES (/*cola*/0, /*colb*/'', /*created_at*/current_timestamp, /*updated_at*/current_timestamp, 0 )
ON DUPLICATE KEY UPDATE colb=VALUES( colb ), updated_at=current_timestamp, lock_version=COALESCE( lock_version , 0 ) + 1