/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.util.ArrayList;
import java.util.List;

import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseMode;
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
	private Lease lease;
	private Report report;
	private Verification verification;

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
		private String checkpointDirectory;
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
		private Bulk bulk = new Bulk();
		private Retry retry = new Retry();
	}

	/** Vendor-neutral bulk copy controls. */
	@Getter
	@Setter
	public static class Bulk {
		private Integer batchSize;
		private Integer bulkCopyTimeout;
		private boolean checkConstraints;
		private boolean fireTriggers;
		private boolean keepIdentity;
		private boolean keepNulls;
		private boolean tableLock;
		private boolean useTransaction;
		private boolean allowEncryptedValueModifications;
	}

	/** Per-chunk retry policy. */
	@Getter
	@Setter
	public static class Retry {
		private int maxRetries;
		private long initialBackoffMillis = 1_000;
		private double backoffMultiplier = 2d;
		private long maxBackoffMillis = 30_000;
		private boolean retryTransientExceptions = true;
		private List<String> sqlStates = new ArrayList<>();
		private List<Integer> errorCodes = new ArrayList<>();
	}

	/** Optional cross-process execution lease. */
	@Getter
	@Setter
	public static class Lease {
		private BulkMigrationJobLeaseMode mode;
		private String ownerId;
		private long durationSeconds =
				BulkMigrationJobLeaseConfiguration.DEFAULT_DURATION.toSeconds();
		private String tableName;
		private String directory;
	}

	/** Optional operational report refreshed at job and task boundaries. */
	@Getter
	@Setter
	public static class Report {
		private String targetFile;
		private BulkMigrationOperationalReportFailurePolicy failurePolicy =
				BulkMigrationOperationalReportFailurePolicy.FAIL_JOB;
	}

	/** Optional ordered source/target count and chunk-hash verification. */
	@Getter
	@Setter
	public static class Verification {
		private boolean enabled = true;
		private int chunkSize = 10_000;
		private boolean failOnMismatch = true;
		private String targetFile;
	}
}
