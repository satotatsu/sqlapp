/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import lombok.Value;

/** Result of one invocation, including rows skipped from an earlier run. */
@Value
public class ChunkedBulkMigrationResult {
	long previouslyProcessedRows;
	long processedRows;
	long completedChunks;
	boolean alreadyComplete;
}
