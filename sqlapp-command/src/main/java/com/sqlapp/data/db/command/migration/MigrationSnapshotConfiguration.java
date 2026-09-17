/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseMode;

import lombok.Getter;
import lombok.Setter;

/** Connection-free YAML definition of one atomic SCD2 snapshot application. */
@Getter
@Setter
public class MigrationSnapshotConfiguration {
	private String schemaFile;
	private String sourceTable;
	private String targetTable;
	private List<String> keyColumns = new ArrayList<>();
	private List<String> trackedColumns = new ArrayList<>();
	private String validFromColumn;
	private String validToColumn;
	private String currentColumn;
	private boolean expireMissingRows = true;
	private Instant effectiveAt;
	private int fetchSize = 10_000;
	private int batchSize = 10_000;
	private Duration approvalValidFor;
	private String approvalReportFile;
	private String reportFile;
	private String failureReportFile;
	private Lease lease;

	/** Optional cross-process fence for one snapshot ID and configuration fingerprint. */
	@Getter
	@Setter
	public static class Lease {
		private BulkMigrationJobLeaseMode mode;
		private String ownerId;
		private long durationSeconds = BulkMigrationJobLeaseConfiguration.DEFAULT_DURATION.toSeconds();
		private String tableName;
		private String directory;
	}
}
