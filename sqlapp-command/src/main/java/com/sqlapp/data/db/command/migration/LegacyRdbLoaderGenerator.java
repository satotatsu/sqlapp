/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.sqlapp.data.schemas.migration.LegacyMigrationContract;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.DataSet;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.Field;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan.JoinKey;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan.LoadDataSet;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan.LoadField;
import com.sqlapp.exceptions.CommandException;

/**
 * Creates the load plan, persistent staging DDL and JdbcTreeDataSession runner
 * template.
 */
public class LegacyRdbLoaderGenerator {

	public LegacyMigrationLoadPlan plan(File contractFile, File schemaFile,
			LegacyMigrationContract contract, String operationMode, int rootBatchSize,
			long commitEveryRootBatches, boolean deleteCommittedRoots, String stagingTablePrefix) {
		new LegacyMigrationContractValidator().validate(contract);
		if (rootBatchSize <= 0 || commitEveryRootBatches <= 0) {
			throw new CommandException(
					"rootBatchSize and commitEveryRootBatches must be greater than zero.");
		}
		if (operationMode == null
				|| !Set.of("INSERT", "INSERT_IGNORE", "MERGE", "REPLACE").contains(operationMode)) {
			throw new CommandException("Unsupported table operation mode: " + operationMode);
		}
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		plan.setMigrationId(contract.getMigrationId());
		plan.setContractFile(contractFile.getPath());
		plan.setContractFingerprint(new LegacyMigrationMappingValidator().fingerprint(contractFile));
		plan.setSchemaFile(schemaFile.getPath());
		plan.setSchemaFingerprint(new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		plan.setTableOperationMode(operationMode);
		plan.setRootBatchSize(rootBatchSize);
		plan.setCommitEveryRootBatches(commitEveryRootBatches);
		plan.setDeleteCommittedRoots(deleteCommittedRoots);
		for (DataSet source : contract.getDataSets()) {
			LoadDataSet target = new LoadDataSet();
			target.setId(source.getId());
			target.setFileName(source.getFileName());
			target.setStagingTable(stagingTablePrefix + source.getTargetTable());
			target.setTargetSchema(source.getTargetSchema());
			target.setTargetTable(source.getTargetTable());
			target.setParentDataSetId(source.getParentDataSetId());
			target.setHierarchyDepth(source.getHierarchyDepth());
			target.setLoadOrder(source.getLoadOrder());
			target.setSourceBusinessKey(new ArrayList<>(source.getSourceBusinessKey()));
			target.setTargetPrimaryKey(new ArrayList<>(source.getTargetPrimaryKey()));
			int csvPosition = 1;
			for (Field field : source.getFields()) {
				if (!field.isExtracted() && !field.isGenerated()) {
					continue;
				}
				LoadField loadField = new LoadField();
				loadField.setCsvPosition(field.isExtracted() ? csvPosition++ : 0);
				loadField.setStagingColumn(field.getStagingColumn());
				loadField.setTargetColumn(field.getTargetColumn());
				loadField.setDataType(field.getTargetDataType());
				loadField.setLength(field.getLength());
				loadField.setScale(field.getScale());
				loadField.setExtracted(field.isExtracted());
				loadField.setTargetGenerated(field.isGenerated() && !field.isOccurrenceIndex());
				loadField.setAction(field.getAction());
				target.getFields().add(loadField);
			}
			if (!source.getAncestorKeys().isEmpty()) {
				source.getAncestorKeys().getFirst().getColumns().forEach(column -> {
					JoinKey key = new JoinKey();
					key.setParentStagingColumn(column.getAncestorColumn());
					key.setChildStagingColumn(column.getSourceColumn());
					key.setTargetForeignKeyColumn(column.getTargetColumn());
					target.getParentJoinKeys().add(key);
				});
			}
			plan.getDataSets().add(target);
		}
		return plan;
	}

	public String stagingDdl(LegacyMigrationLoadPlan plan, boolean quoteIdentifiers) {
		StringBuilder builder = new StringBuilder();
		line(builder, "-- Persistent staging tables for restartable legacy migration.");
		line(builder, "-- Run in the migration staging schema. Review data types for the target dialect.");
		for (LoadDataSet dataSet : plan.getDataSets()) {
			line(builder, "");
			line(builder, "CREATE TABLE " + identifier(dataSet.getStagingTable(), quoteIdentifiers));
			line(builder, "(");
			List<LoadField> fields = distinctStagingFields(dataSet);
			for (int i = 0; i < fields.size(); i++) {
				LoadField field = fields.get(i);
				line(builder, "    " + (i == 0 ? "  " : ", ")
						+ identifier(field.getStagingColumn(), quoteIdentifiers) + " " + sqlType(field));
			}
			if (dataSet.getParentDataSetId() == null) {
				line(builder, "    , SQLAPP_LOAD_STATUS VARCHAR(16) DEFAULT 'PENDING' NOT NULL");
			}
			line(builder, "    , SQLAPP_LOADED_AT TIMESTAMP");
			line(builder, ");");
			List<String> indexColumns = indexColumns(dataSet);
			if (!indexColumns.isEmpty()) {
				line(builder, "CREATE INDEX " + identifier(indexName(dataSet), quoteIdentifiers) + " ON "
						+ identifier(dataSet.getStagingTable(), quoteIdentifiers) + " ("
						+ indexColumns.stream().map(name -> identifier(name, quoteIdentifiers))
								.reduce((left, right) -> left + ", " + right).orElse("") + ");");
			}
		}
		return builder.toString();
	}

	public String importConfiguration(LegacyMigrationLoadPlan plan, LegacyMigrationContract contract) {
		StringBuilder builder = new StringBuilder();
		line(builder, "# CSV-to-staging import settings");
		line(builder, "encoding: " + yaml(contract.getCsv().getEncoding()));
		line(builder, "delimiter: " + yaml(contract.getCsv().getDelimiter()));
		line(builder, "quote: " + yaml(contract.getCsv().getQuote()));
		line(builder, "nullValue: " + yaml(contract.getCsv().getNullValue()));
		line(builder, "header: " + contract.getCsv().isHeader());
		line(builder, "failOnMissingFile: true");
		line(builder, "failOnExtraColumn: true");
		line(builder, "dataSets:");
		for (LoadDataSet dataSet : plan.getDataSets()) {
			line(builder, "  - id: " + yaml(dataSet.getId()));
			line(builder, "    file: " + yaml(dataSet.getFileName()));
			line(builder, "    stagingTable: " + yaml(dataSet.getStagingTable()));
			line(builder, "    columns:");
			dataSet.getFields().stream().filter(LoadField::isExtracted).forEach(field ->
					line(builder, "      - {position: " + field.getCsvPosition() + ", name: "
							+ yaml(field.getStagingColumn()) + "}"));
		}
		return builder.toString();
	}

	public String runnerTemplate(LegacyMigrationLoadPlan plan, String className) {
		StringBuilder builder = new StringBuilder();
		line(builder, "/* Generated runner template. Supply DataSource and staging row readers. */");
		line(builder, "import java.sql.Connection;");
		line(builder, "import java.util.ArrayList;");
		line(builder, "import java.util.List;");
		line(builder, "import javax.sql.DataSource;");
		line(builder, "import com.sqlapp.data.schemas.Row;");
		line(builder, "import com.sqlapp.data.schemas.SchemaUtils;");
		line(builder, "import com.sqlapp.jdbc.sql.JdbcTreeDataSession;");
		line(builder, "import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;");
		line(builder, "");
		line(builder, "public class " + className + " {");
		line(builder, "  private static final int ROOT_BATCH_SIZE = " + plan.getRootBatchSize() + ";");
		line(builder, "  private static final long COMMIT_EVERY_ROOT_BATCHES = "
				+ plan.getCommitEveryRootBatches() + "L;");
		line(builder, "  private final DataSource dataSource;");
		line(builder, "  public " + className + "(DataSource dataSource) { this.dataSource = dataSource; }");
		line(builder, "  public void run() throws Exception {");
		line(builder, "    try (Connection connection = dataSource.getConnection()) {");
		line(builder, "      connection.setAutoCommit(false);");
		line(builder, "      var schema = SchemaUtils.readXml(new java.io.File("
				+ javaString(plan.getSchemaFile()) + "));");
		line(builder, "      List<Row> committedRoots = new ArrayList<>();");
		line(builder, "      try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection,");
		line(builder, "          com.sqlapp.data.schemas.SchemaUtils.toTables(schema))) {");
		line(builder, "        session.setRootBatchSize(ROOT_BATCH_SIZE);");
		line(builder, "        session.setCommitEveryRootBatches(COMMIT_EVERY_ROOT_BATCHES);");
		line(builder, "        session.setTableOperationMode(TableOperationMode."
				+ plan.getTableOperationMode() + ");");
		line(builder, "        session.setAfterRootBatchHandler((batch, table, rows) -> committedRoots.addAll(rows));");
		if (plan.isDeleteCommittedRoots()) {
			line(builder, "        session.setBeforeCommitEveryRootBatchesHandler((commit, lastRoot) -> {");
			line(builder, "          deleteStagingHierarchy(connection, committedRoots);");
			line(builder, "        });");
			line(builder, "        session.setAfterCommitEveryRootBatchesHandler((commit, lastRoot) -> committedRoots.clear());");
		}
		line(builder, "        readStagingHierarchy(connection, session);");
		line(builder, "      } catch (Exception e) {");
		line(builder, "        connection.rollback();");
		line(builder, "        throw e;");
		line(builder, "      }");
		line(builder, "    }");
		line(builder, "  }");
		line(builder, "  private void readStagingHierarchy(Connection connection, JdbcTreeDataSession session)");
		line(builder, "      throws Exception {");
		line(builder, "    /* TODO: Select PENDING roots in business-key order, then select each child");
		line(builder, "       using parentJoinKeys from the YAML plan. Create parent rows before child rows.");
		line(builder, "       JdbcTreeDataSession propagates generated parent IDs to child foreign keys. */");
		line(builder, "  }");
		line(builder, "  private void deleteStagingHierarchy(Connection connection, List<Row> roots)");
		line(builder, "      throws Exception {");
		line(builder, "    /* TODO: Delete descendants first and roots last using original business keys.");
		line(builder, "       This callback runs BEFORE the same commit as target updates. */");
		line(builder, "  }");
		line(builder, "}");
		return builder.toString();
	}

	private List<LoadField> distinctStagingFields(LoadDataSet dataSet) {
		Map<String, LoadField> fields = new LinkedHashMap<>();
		for (LoadField field : dataSet.getFields()) {
			if (field.isExtracted() && field.getStagingColumn() != null) {
				fields.putIfAbsent(field.getStagingColumn().toLowerCase(Locale.ROOT), field);
			}
		}
		return new ArrayList<>(fields.values());
	}

	private List<String> indexColumns(LoadDataSet dataSet) {
		Set<String> fields = new LinkedHashSet<>();
		fields.addAll(dataSet.getSourceBusinessKey());
		dataSet.getParentJoinKeys().forEach(key -> fields.add(key.getChildStagingColumn()));
		Set<String> staging = distinctStagingFields(dataSet).stream()
				.map(field -> field.getStagingColumn().toLowerCase(Locale.ROOT))
				.collect(java.util.stream.Collectors.toSet());
		return fields.stream().filter(name -> name != null && staging.contains(name.toLowerCase(Locale.ROOT)))
				.toList();
	}

	private String indexName(LoadDataSet dataSet) {
		String value = "IX_" + dataSet.getStagingTable() + "_KEY";
		return value.length() <= 60 ? value : value.substring(0, 51) + "_"
				+ String.format(Locale.ROOT, "%08X", value.hashCode());
	}

	private String sqlType(LoadField field) {
		String type = field.getDataType();
		if (type == null || type.isBlank()) {
			return "VARCHAR(4000)";
		}
		String upper = type.toUpperCase(Locale.ROOT);
		if (field.getLength() != null && !upper.contains("(")
				&& (upper.contains("CHAR") || upper.contains("BINARY"))) {
			return upper + "(" + field.getLength() + ")";
		}
		if (field.getScale() != null && field.getLength() != null && !upper.contains("(")
				&& (upper.contains("DECIMAL") || upper.contains("NUMERIC"))) {
			return upper + "(" + field.getLength() + "," + field.getScale() + ")";
		}
		return upper;
	}

	private String identifier(String value, boolean quote) {
		if (value == null || value.isBlank()) {
			throw new CommandException("Staging identifier must not be empty.");
		}
		if (!quote && !value.matches("[A-Za-z_][A-Za-z0-9_$#]*")) {
			throw new CommandException("Identifier requires quoting: " + value);
		}
		return quote ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
	}

	private String yaml(String value) {
		return "\"" + (value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
				.replace("\r", "\\r").replace("\n", "\\n")) + "\"";
	}

	private String javaString(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private void line(StringBuilder builder, String value) {
		builder.append(value).append(System.lineSeparator());
	}
}
