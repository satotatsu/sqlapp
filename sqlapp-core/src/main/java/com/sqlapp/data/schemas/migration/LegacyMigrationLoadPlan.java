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

	private String tableOperationMode = "INSERT_IGNORE";

	/**
	 * Maximum root rows sent in one JDBC batch.
	 */
	private int rootBatchSize = 500;

	/**
	 * Number of completed root JDBC batches between commits.
	 */
	private long commitEveryRootBatches = 500;

	private boolean deleteCommittedRoots = true;

	private TransactionPolicy transaction = new TransactionPolicy();

	private List<LoadDataSet> dataSets = new ArrayList<>();

	@Getter
	@Setter
	public static class TransactionPolicy {
		private boolean autoCommit = false;
		private String commitUnit = "ROOT_BATCH";
		private String stagingDeleteTiming = "BEFORE_COMMIT";
		private boolean targetAndStagingDeleteAtomic = true;
		private String restartUnit = "ROOT";
	}

	@Getter
	@Setter
	public static class LoadDataSet {
		private String id;
		private String fileName;
		private String stagingTable;
		private String targetSchema;
		private String targetTable;
		private String parentDataSetId;
		private int hierarchyDepth;
		private int loadOrder;
		private List<String> sourceBusinessKey = new ArrayList<>();
		private List<String> targetPrimaryKey = new ArrayList<>();
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
		private String targetForeignKeyColumn;
	}
}
