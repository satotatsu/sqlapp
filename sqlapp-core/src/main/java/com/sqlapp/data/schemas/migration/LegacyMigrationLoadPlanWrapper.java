/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

import java.util.List;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan.JoinKey;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan.LoadDataSet;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan.LoadField;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan.TransactionPolicy;

import lombok.Getter;
import lombok.Setter;

/**
 * Executable contract for staging CSV data and loading it through
 * JdbcTreeDataSession.
 */
@Getter
@Setter
public class LegacyMigrationLoadPlanWrapper {

	private final LegacyMigrationLoadPlan inner;

	private final List<LoadDataSetWrapper> dataSets;

	public LegacyMigrationLoadPlanWrapper(LegacyMigrationLoadPlan inner) {
		this.inner = inner;
		this.dataSets = inner.getDataSets().stream().map(o -> new LoadDataSetWrapper(o)).toList();
	}

	public String getFormat() {
		return this.inner.getFormat();
	}

	public int getVersion() {
		return this.inner.getVersion();
	}

	public String getMigrationId() {
		return this.inner.getMigrationId();
	}

	public String getContractFile() {
		return this.inner.getContractFile();
	}

	public String getContractFingerprint() {
		return this.inner.getContractFingerprint();
	}

	public String getSchemaFile() {
		return this.inner.getSchemaFile();
	}

	public String getSchemaFingerprint() {
		return this.inner.getSchemaFingerprint();
	}

	public String getViewpointId() {
		return this.inner.getViewpointId();
	}

	public String getViewpointsFile() {
		return this.inner.getViewpointsFile();
	}

	public String getViewpointsFingerprint() {
		return this.inner.getViewpointsFingerprint();
	}

	public List<String> getResolvedTableIds() {
		return this.inner.getResolvedTableIds();
	}

	public List<String> getResolvedDataSetIds() {
		return this.inner.getResolvedDataSetIds();
	}

	public String getTableOperationMode() {
		return this.inner.getTableOperationMode();
	}

	public int getRootBatchSize() {
		return this.inner.getRootBatchSize();
	}

	public long getCommitEveryRootBatches() {
		return this.inner.getCommitEveryRootBatches();
	}

	public boolean isDeleteCommittedRoots() {
		return this.inner.isDeleteCommittedRoots();
	}

	public String getRootCursorStrategy() {
		return this.inner.getRootCursorStrategy();
	}

	public TransactionPolicy getTransaction() {
		return this.inner.getTransaction();
	}

	public List<LoadDataSetWrapper> getDataSets() {
		return this.dataSets;
	}

	@Getter
	@Setter
	public static class LoadDataSetWrapper {
		private final LoadDataSet inner;
		private final List<LoadFieldWrapper> fields;
		private final List<JoinKeyWrapper> parentJoinKeys;

		public String getId() {
			return this.inner.getId();
		}

		public String getFileName() {
			return this.inner.getFileName();
		}

		public String getParentDataSetId() {
			return this.inner.getParentDataSetId();
		}

		public int getHierarchyDepth() {
			return this.inner.getHierarchyDepth();
		}

		public int getLoadOrder() {
			return this.inner.getLoadOrder();
		}

		public List<String> getSourceBusinessKey() {
			return this.inner.getSourceBusinessKey();
		}

		public List<String> getTargetPrimaryKey() {
			return this.inner.getTargetPrimaryKey();
		}

		public LoadDataSetWrapper(final LoadDataSet inner) {
			this.inner = inner;
			fields = inner.getFields().stream().map(o -> new LoadFieldWrapper(o)).toList();
			parentJoinKeys = inner.getParentJoinKeys().stream().map(o -> new JoinKeyWrapper(o)).toList();
		}

		private Table stagingTable;
		private Table targetTable;
	}

	@Getter
	@Setter
	public static class LoadFieldWrapper {
		private final LoadField inner;

		public LoadFieldWrapper(LoadField inner) {
			this.inner = inner;
		}

		public int getCsvPosition() {
			return this.inner.getCsvPosition();
		}

		public String getDataType() {
			return this.inner.getDataType();
		}

		public Long getLength() {
			return this.inner.getLength();
		}

		public Integer getScale() {
			return this.inner.getScale();
		}

		public boolean isExtracted() {
			return this.inner.isExtracted();
		}

		public boolean isTargetGenerated() {
			return this.inner.isTargetGenerated();
		}

		public String getAction() {
			return this.inner.getAction();
		}

		private Column stagingColumn;
		private Column targetColumn;
	}

	@Getter
	@Setter
	public static class JoinKeyWrapper {
		private final JoinKey inner;

		public JoinKeyWrapper(JoinKey inner) {
			this.inner = inner;
		}

		private Column parentStagingColumn;
		private Column childStagingColumn;
		private Column targetForeignKeyColumn;
	}
}
