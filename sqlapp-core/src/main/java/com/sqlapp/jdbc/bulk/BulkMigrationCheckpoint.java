/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.io.Serializable;

import lombok.Builder;
import lombok.Value;

/** Durable progress recorded only after a migration chunk succeeds. */
@Value
@Builder(toBuilder = true)
public class BulkMigrationCheckpoint implements Serializable {
	private static final long serialVersionUID = 1L;
	String migrationId;
	String sourceFingerprint;
	String targetFingerprint;
	long processedRows;
	long completedChunks;
	String lastChunkHash;
	String resumeToken;
	boolean complete;
}
