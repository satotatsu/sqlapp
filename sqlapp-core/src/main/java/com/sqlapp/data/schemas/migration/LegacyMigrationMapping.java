/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * Versioned, machine-readable lineage between a legacy schema and its
 * modernized relational schema.
 */
@Getter
@Setter
public class LegacyMigrationMapping {

	public static final String FORMAT = "sqlapp-legacy-migration";

	public static final int CURRENT_VERSION = 1;

	private String format = FORMAT;

	private int version = CURRENT_VERSION;

	private MigrationMetadata migration = new MigrationMetadata();

	private SchemaEndpoint source = new SchemaEndpoint();

	private SchemaEndpoint target = new SchemaEndpoint();

	private Map<String, Object> options = new LinkedHashMap<>();

	private List<TableMapping> tables = new ArrayList<>();

	private List<RelationshipMapping> relationships = new ArrayList<>();

	private List<TransformationRecord> transformations = new ArrayList<>();

	private Diagnostics diagnostics = new Diagnostics();

	private Statistics statistics = new Statistics();

	@Getter
	@Setter
	public static class MigrationMetadata {
		private String id;
		private String title;
		private String description;
		private String generatedAt;
		private Generator generator = new Generator();
	}

	@Getter
	@Setter
	public static class Generator {
		private String name = "sqlapp-command";
		private String version;
	}

	@Getter
	@Setter
	public static class SchemaEndpoint {
		private String system;
		private String database;
		private String schemaFile;
		private String schemaFingerprint;
		private List<DefinitionFile> definitionFiles = new ArrayList<>();
	}

	@Getter
	@Setter
	public static class DefinitionFile {
		private String path;
		private String type;
		private String start;
		private String end;
	}

	@Getter
	@Setter
	public static class TableMapping {
		private String id;
		private TableReference source = new TableReference();
		private TableReference target = new TableReference();
		private TableRole role = TableRole.ROOT;
		private TableOperation operation = TableOperation.COPY;
		private ParentMapping parent;
		private KeyMapping keys = new KeyMapping();
		private List<ColumnMapping> columns = new ArrayList<>();
		private List<ConstraintMapping> constraints = new ArrayList<>();
		private Map<String, Object> details = new LinkedHashMap<>();
	}

	@Getter
	@Setter
	public static class TableReference {
		private String catalog;
		private String schema;
		private String table;
		private String path;
	}

	@Getter
	@Setter
	public static class ParentMapping {
		private String mappingId;
		private List<ColumnPair> sourceReference = new ArrayList<>();
		private List<ColumnPair> resolvedReference = new ArrayList<>();
	}

	@Getter
	@Setter
	public static class KeyMapping {
		private List<String> sourcePrimaryKey = new ArrayList<>();
		private List<String> targetPrimaryKey = new ArrayList<>();
		private List<String> businessKey = new ArrayList<>();
		private List<String> targetUniqueKey = new ArrayList<>();
		private GeneratedKey generatedKey;
	}

	@Getter
	@Setter
	public static class GeneratedKey {
		private String column;
		private String dataType;
		private String generationType;
		private String sequence;
	}

	@Getter
	@Setter
	public static class ColumnMapping {
		private String source;
		private String sourcePath;
		private String target;
		private ColumnAction action = ColumnAction.COPY;
		private ColumnDefinition sourceDefinition;
		private ColumnDefinition targetDefinition;
		private Map<String, Object> conversion = new LinkedHashMap<>();
		private Reason reason;
		private List<IndexedSourceColumn> sourceColumns = new ArrayList<>();
	}

	@Getter
	@Setter
	public static class IndexedSourceColumn {
		private Integer index;
		private String column;
	}

	@Getter
	@Setter
	public static class ColumnDefinition {
		private String dataType;
		private Long length;
		private Integer scale;
		private Boolean nullable;
	}

	@Getter
	@Setter
	public static class Reason {
		private String code;
		private String message;
	}

	@Getter
	@Setter
	public static class ConstraintMapping {
		private String type;
		private String sourceName;
		private String targetName;
		private List<String> sourceColumns = new ArrayList<>();
		private List<String> targetColumns = new ArrayList<>();
		private String derivedFrom;
	}

	@Getter
	@Setter
	public static class RelationshipMapping {
		private String id;
		private RelationshipType type = RelationshipType.HIERARCHICAL;
		private int depth;
		private String parentMappingId;
		private String childMappingId;
		private List<ColumnPair> sourceKeys = new ArrayList<>();
		private List<ColumnPair> targetKeys = new ArrayList<>();
		private boolean parentIdPropagation;
		private int loadOrder;
	}

	@Getter
	@Setter
	public static class ColumnPair {
		private String parentColumn;
		private String childColumn;

		public ColumnPair() {
		}

		public ColumnPair(String parentColumn, String childColumn) {
			this.parentColumn = parentColumn;
			this.childColumn = childColumn;
		}
	}

	@Getter
	@Setter
	public static class TransformationRecord {
		private int sequence;
		private String command;
		private String status;
		private Map<String, Object> configuration = new LinkedHashMap<>();
		private Map<String, Object> changes = new LinkedHashMap<>();
	}

	@Getter
	@Setter
	public static class Diagnostics {
		private List<Diagnostic> warnings = new ArrayList<>();
		private List<Diagnostic> skipped = new ArrayList<>();
		private List<Diagnostic> errors = new ArrayList<>();
	}

	@Getter
	@Setter
	public static class Diagnostic {
		private String code;
		private DiagnosticSeverity severity;
		private String tableMappingId;
		private String action;
		private String message;
		private Map<String, Object> details = new LinkedHashMap<>();
	}

	@Getter
	@Setter
	public static class Statistics {
		private int sourceTableCount;
		private int targetTableCount;
		private int transformedTableCount;
		private int warningCount;
		private int skippedCount;
		private int errorCount;
	}

	public enum TableRole {
		ROOT, CHILD, DETAIL, LOOKUP, STAGING
	}

	public enum TableOperation {
		COPY, TRANSFORM, SPLIT, MERGE, SKIP
	}

	public enum ColumnAction {
		COPY, RENAME, CAST, GENERATE, CONSTANT, DERIVE, SPLIT, COMBINE, DROP, REFERENCE
	}

	public enum RelationshipType {
		HIERARCHICAL, REFERENCE
	}

	public enum DiagnosticSeverity {
		INFO, WARNING, ERROR
	}
}
