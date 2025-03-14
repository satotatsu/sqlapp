INSERT INTO `tableA` ( cola, colb, created_at, updated_at, lock_version )
VALUES (/*cola*/0, /*colb*/'', current_timestamp, COALESCE(/*updated_at*/current_timestamp, current_timestamp), 0 )
ON DUPLICATE KEY UPDATE colb = VALUES( colb ), updated_at = COALESCE(/*updated_at*/current_timestamp, current_timestamp), lock_version = COALESCE( VALUES ( lock_version ), 0) + 1