/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.Map;

import com.sqlapp.data.schemas.Table;

import lombok.Builder;
import lombok.Getter;

/** One table migration and its dependency IDs within a job. */
@Getter
@Builder
public class BulkMigrationJobTask {
	private final String taskId;
	private final Table sourceTable;
	private final BulkMigrationKeysetSource keysetSource;
	private final Table targetTable;
	private final Map<String, String> columnMappings;
	private final ChunkedBulkMigrationOption options;
	private final BulkMigrationCheckpointStore checkpointStore;
	private final ChunkedBulkMigrationListener chunkListener;

	private BulkMigrationJobTask(final String taskId, final Table sourceTable,
			final BulkMigrationKeysetSource keysetSource,
			final Table targetTable,
			final Map<String, String> columnMappings,
			final ChunkedBulkMigrationOption options,
			final BulkMigrationCheckpointStore checkpointStore,
			final ChunkedBulkMigrationListener chunkListener) {
		if (taskId == null || taskId.isBlank()) {
			throw new IllegalArgumentException("taskId must not be empty");
		}
		if ((sourceTable == null) == (keysetSource == null)) {
			throw new IllegalArgumentException(
					"Task must have exactly one Table or keyset source: " + taskId);
		}
		this.taskId = taskId;
		this.sourceTable = sourceTable;
		this.keysetSource = keysetSource;
		this.targetTable = targetTable;
		this.columnMappings = columnMappings == null ? Map.of() : Map.copyOf(columnMappings);
		if (!this.columnMappings.isEmpty() && targetTable == null) {
			throw new IllegalArgumentException("targetTable is required when columnMappings are configured: " + taskId);
		}
		if (targetTable != null) {
			final Table source = sourceTable != null ? sourceTable : keysetSource.getTable();
			if (source.getColumns().size() != targetTable.getColumns().size()) {
				throw new IllegalArgumentException("Source and target column counts differ: " + taskId);
			}
			final var names = new java.util.HashSet<String>();
			for (final var sourceColumn : source.getColumns()) {
				final String targetName = this.columnMappings.getOrDefault(sourceColumn.getName(), sourceColumn.getName());
				if (targetTable.getColumns().get(targetName) == null
						|| !names.add(targetName.toLowerCase(java.util.Locale.ROOT))) {
					throw new IllegalArgumentException("Invalid one-to-one target column mapping: "
							+ sourceColumn.getName() + " -> " + targetName);
				}
			}
			if (this.columnMappings.keySet().stream().anyMatch(name -> source.getColumns().get(name) == null)) {
				throw new IllegalArgumentException("columnMappings contains an unknown source column: " + taskId);
			}
		}
		this.options = java.util.Objects.requireNonNull(options, "options");
		this.checkpointStore = checkpointStore;
		this.chunkListener = chunkListener;
	}

	public String getTargetColumnName(final String sourceColumnName) {
		return columnMappings.getOrDefault(sourceColumnName, sourceColumnName);
	}

	public Table getEffectiveSourceTable() {
		return sourceTable != null ? sourceTable : keysetSource.getTable();
	}

	public Table getEffectiveTargetTable() {
		return targetTable != null ? targetTable : getEffectiveSourceTable();
	}
}
