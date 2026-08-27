/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import lombok.Value;

/** Progress of one migration chunk, using the durable checkpoint counters. */
@Value
public class ChunkedBulkMigrationProgress {
	String migrationId;
	long chunkIndex;
	int chunkRows;
	long processedRowsBefore;
	long processedRowsAfter;
}
