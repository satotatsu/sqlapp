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
 * Contract shared by legacy extractors, staging loaders and hierarchical
 * database writers.
 */
@Getter
@Setter
public class LegacyMigrationContract {

	public static final String FORMAT = "sqlapp-legacy-migration-contract";

	public static final int CURRENT_VERSION = 1;

	private String format = FORMAT;

	private int version = CURRENT_VERSION;

	private String migrationId;

	private String mappingFile;

	private String mappingFingerprint;

	private CsvFormat csv = new CsvFormat();

	private List<DataSet> dataSets = new ArrayList<>();

	@Getter
	@Setter
	public static class CsvFormat {
		private String encoding = "UTF-8";
		private String delimiter = ",";
		private String quote = "\"";
		private String nullValue = "";
		private boolean header = true;
		private String recordSeparator = "CRLF";
	}

	@Getter
	@Setter
	public static class DataSet {
		private String id;
		private String sourcePath;
		private String fileName;
		private String targetCatalog;
		private String targetSchema;
		private String targetTable;
		private String stagingTable;
		private int hierarchyDepth;
		private int loadOrder;
		private String parentDataSetId;
		private Integer maximumOccurrences;
		private String occurrenceColumn;
		private List<String> sourceBusinessKey = new ArrayList<>();
		private List<String> targetPrimaryKey = new ArrayList<>();
		private List<Field> fields = new ArrayList<>();
		private List<AncestorKey> ancestorKeys = new ArrayList<>();
	}

	@Getter
	@Setter
	public static class Field {
		private int position;
		private String sourcePath;
		private String sourceColumn;
		private String stagingColumn;
		private String targetColumn;
		private String targetDataType;
		private Long length;
		private Integer scale;
		private Boolean nullable;
		private String action;
		private boolean extracted;
		private boolean generated;
		private boolean occurrenceIndex;
		private String remarks;
	}

	@Getter
	@Setter
	public static class AncestorKey {
		private String ancestorDataSetId;
		private String ancestorTable;
		private int depth;
		private List<KeyColumn> columns = new ArrayList<>();
	}

	@Getter
	@Setter
	public static class KeyColumn {
		private String ancestorColumn;
		private String sourceColumn;
		private String targetColumn;

		public KeyColumn() {
		}

		public KeyColumn(String ancestorColumn, String sourceColumn, String targetColumn) {
			this.ancestorColumn = ancestorColumn;
			this.sourceColumn = sourceColumn;
			this.targetColumn = targetColumn;
		}
	}
}
