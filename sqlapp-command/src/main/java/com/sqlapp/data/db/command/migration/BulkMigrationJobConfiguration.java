/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.util.ArrayList;
import java.util.List;

import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode;
import com.sqlapp.jdbc.bulk.BulkMigrationMode;
import com.sqlapp.jdbc.bulk.BulkUpsertDuplicateKeyStrategy;

import lombok.Getter;
import lombok.Setter;

/** Serializable, connection-free definition of a bulk migration job. */
@Getter
@Setter
public class BulkMigrationJobConfiguration {
	private String schemaFile;
	private List<Task> tasks = new ArrayList<>();

	@Getter
	@Setter
	public static class Task {
		private String id;
		private String table;
		private List<String> keysetColumns = new ArrayList<>();
		private String migrationId;
		private int chunkSize = 10_000;
		private BulkMigrationMode mode = BulkMigrationMode.UPSERT;
		private boolean resume = true;
		private BulkMigrationCheckpointMode checkpointMode =
				BulkMigrationCheckpointMode.DATABASE;
		private String checkpointTableName = "SQLAPP_BULK_MIGRATION_CHECKPOINT";
		private String sourceFingerprint;
		private String targetFingerprint;
		private List<String> keyColumns = new ArrayList<>();
		private List<String> updateColumns = new ArrayList<>();
		private boolean updateWhenMatched = true;
		private boolean insertWhenNotMatched = true;
		private boolean useTransaction = true;
		private BulkUpsertDuplicateKeyStrategy duplicateKeyStrategy =
				BulkUpsertDuplicateKeyStrategy.ERROR;
		private String stagingTableName;
	}
}
