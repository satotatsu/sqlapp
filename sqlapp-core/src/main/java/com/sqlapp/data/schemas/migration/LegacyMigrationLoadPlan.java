/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Executable contract for staging CSV data and loading it through
 * JdbcTreeDataSession.
 */
@Getter
@Setter
public class LegacyMigrationLoadPlan {

	public static final String FORMAT = "sqlapp-legacy-rdb-load-plan";

	public static final int CURRENT_VERSION = 1;

	private String format = FORMAT;

	private int version = CURRENT_VERSION;

	private String migrationId;

	private String contractFile;

	private String contractFingerprint;

	private String schemaFile;

	private String schemaFingerprint;

	private String viewpointId;

	private String viewpointsFile;

	private String viewpointsFingerprint;

	private List<String> resolvedTableIds = new ArrayList<>();

	private List<String> resolvedDataSetIds = new ArrayList<>();

	private TableOperationMode tableOperationMode = TableOperationMode.INSERT_IGNORE;

	private String stagingTablePrefix;

	/**
	 * Maximum root rows sent in one JDBC batch.
	 */
	private int rootBatchSize = 500;

	/**
	 * Number of completed root JDBC batches between commits.
	 */
	private long commitEveryRootBatches = 500;

	private boolean deleteCommittedRoots = true;

	private RootCursorStrategy rootCursorStrategy = RootCursorStrategy.DIALECT;

	private TransactionPolicy transaction = new TransactionPolicy();

	private List<LoadDataSet> dataSets = new ArrayList<>();

	public enum TableOperationMode {
		INSERT, INSERT_IGNORE, MERGE, REPLACE
	}

	public enum RootCursorStrategy {
		DIALECT, HOLD, REOPEN
	}

	public enum CommitUnit {
		ROOT_BATCH
	}

	public enum StagingDeleteTiming {
		BEFORE_COMMIT
	}

	public enum RestartUnit {
		ROOT
	}

	@Getter
	@Setter
	public static class TransactionPolicy {
		private boolean autoCommit = false;
		private CommitUnit commitUnit = CommitUnit.ROOT_BATCH;
		private StagingDeleteTiming stagingDeleteTiming = StagingDeleteTiming.BEFORE_COMMIT;
		private boolean targetAndStagingDeleteAtomic = true;
		private RestartUnit restartUnit = RestartUnit.ROOT;
	}

	@Getter
	@Setter
	public static class LoadDataSet {
		private String id;
		private String fileName;
		private String stagingTable;
		private String targetCatalog;
		private String targetSchema;
		private String targetTable;
		private String parentDataSetId;
		private int hierarchyDepth;
		private int loadOrder;
		private List<String> sourceBusinessKey = new ArrayList<>();
		private List<String> targetPrimaryKey = new ArrayList<>();
		private List<String> targetForeignKey = new ArrayList<>();
		private List<LoadField> fields = new ArrayList<>();
		private List<JoinKey> parentJoinKeys = new ArrayList<>();
	}

	@Getter
	@Setter
	public static class LoadField {
		private int csvPosition;
		private String stagingColumn;
		private String targetColumn;
		private String dataType;
		private Long length;
		private Integer scale;
		private boolean extracted;
		private boolean targetGenerated;
		private String action;
	}

	@Getter
	@Setter
	public static class JoinKey {
		private String parentStagingColumn;
		private String childStagingColumn;
	}
}
