INSERT INTO "tableA" ( cola, colb, created_at, updated_at, lock_version ) VALUES (/*cola*/0, /*colb*/'', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0 )
ON CONFLICT ON CONSTRAINT "PK_tableA"
DO UPDATE (/*colb*/'', CURRENT_TIMESTAMP, lock_version + 1)