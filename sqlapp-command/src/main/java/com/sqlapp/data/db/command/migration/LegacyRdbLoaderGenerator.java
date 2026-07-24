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

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
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

	public LegacyMigrationLoadPlan plan(File contractFile, File schemaFile, LegacyMigrationContract contract,
			String operationMode, int rootBatchSize, long commitEveryRootBatches, boolean deleteCommittedRoots,
			String stagingTablePrefix, String rootCursorStrategy) {
		new LegacyMigrationContractValidator().validate(contract);
		if (rootBatchSize <= 0 || commitEveryRootBatches <= 0) {
			throw new CommandException("rootBatchSize and commitEveryRootBatches must be greater than zero.");
		}
		if (operationMode == null || !Set.of("INSERT", "INSERT_IGNORE", "MERGE", "REPLACE").contains(operationMode)) {
			throw new CommandException("Unsupported table operation mode: " + operationMode);
		}
		if (rootCursorStrategy == null || !Set.of("DIALECT", "HOLD", "REOPEN").contains(rootCursorStrategy)) {
			throw new CommandException("Unsupported root cursor strategy: " + rootCursorStrategy);
		}
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		plan.setMigrationId(contract.getMigrationId());
		plan.setContractFile(contractFile.getAbsolutePath());
		plan.setContractFingerprint(new LegacyMigrationMappingValidator().fingerprint(contractFile));
		plan.setSchemaFile(schemaFile.getAbsolutePath());
		plan.setSchemaFingerprint(new LegacyMigrationMappingValidator().fingerprint(schemaFile));
		plan.setTableOperationMode(operationMode);
		plan.setRootBatchSize(rootBatchSize);
		plan.setCommitEveryRootBatches(commitEveryRootBatches);
		plan.setDeleteCommittedRoots(deleteCommittedRoots);
		plan.setRootCursorStrategy(rootCursorStrategy);
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

	public String stagingDdl(LegacyMigrationLoadPlan plan, Dialect dialect) {
		Schema schema = new Schema();
		schema.setDialect(dialect);
		List<Table> tables = new ArrayList<>();
		for (LoadDataSet dataSet : plan.getDataSets()) {
			Table table = new Table(dataSet.getStagingTable());
			schema.getTables().add(table);
			for (LoadField field : distinctStagingFields(dataSet)) {
				table.getColumns().add(stagingColumn(field));
			}
			if (dataSet.getParentDataSetId() == null) {
				table.getColumns().add(new Column("SQLAPP_LOAD_STATUS").setDataType(DataType.VARCHAR).setLength(16)
						.setDefaultValue("'PENDING'").setNotNull(true));
			}
			table.getColumns().add(new Column("SQLAPP_LOADED_AT").setDataType(DataType.TIMESTAMP));
			addStagingIndexes(table, dataSet);
			tables.add(table);
		}
		List<SqlOperation> operations = new ArrayList<>();
		var registry = dialect.createSqlFactoryRegistry();
		for (Table table : tables) {
			SqlFactory<Table> sqlFactory = registry.getSqlFactory(table, SqlType.CREATE);
			operations.addAll(sqlFactory.createSql(table));
		}
		return SqlOperation.toText(operations);
	}

	private Column stagingColumn(LoadField field) {
		Column column = new Column(field.getStagingColumn());
		String typeName = field.getDataType();
		if (typeName == null || typeName.isBlank()) {
			column.setDataType(DataType.VARCHAR).setLength(4000);
			return column;
		}
		DataType dataType = DataType.toType(typeName);
		column.setDataType(dataType);
		if (dataType == DataType.OTHER) {
			column.setDataTypeName(typeName);
		}
		column.setLength(field.getLength());
		column.setScale(field.getScale());
		return column;
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
			dataSet.getFields().stream().filter(LoadField::isExtracted)
					.forEach(field -> line(builder, "      - {position: " + field.getCsvPosition() + ", name: "
							+ yaml(field.getStagingColumn()) + "}"));
		}
		return builder.toString();
	}

	public String runnerTemplate(LegacyMigrationLoadPlan plan, String className) {
		StringBuilder builder = new StringBuilder();
		line(builder, "/* Generated runner template. Supply the application DataSource. */");
		line(builder, "import java.sql.Connection;");
		line(builder, "import java.io.File;");
		line(builder, "import javax.sql.DataSource;");
		line(builder, "import com.sqlapp.data.schemas.SchemaUtils;");
		line(builder, "import com.sqlapp.data.db.command.migration.JdbcTreeStagingLoader;");
		line(builder, "import com.sqlapp.data.db.command.migration.LegacyMigrationLoadPlanIO;");
		line(builder, "");
		line(builder, "public class " + className + " {");
		line(builder, "  private final DataSource dataSource;");
		line(builder, "  private final File loadPlanFile;");
		line(builder, "  public " + className + "(DataSource dataSource, File loadPlanFile) {");
		line(builder, "    this.dataSource = dataSource;");
		line(builder, "    this.loadPlanFile = loadPlanFile;");
		line(builder, "  }");
		line(builder, "  public void run() throws Exception {");
		line(builder, "    var plan = new LegacyMigrationLoadPlanIO().read(loadPlanFile);");
		line(builder, "    var schema = SchemaUtils.readXml(new File(plan.getSchemaFile()));");
		line(builder, "    try (Connection connection = dataSource.getConnection()) {");
		line(builder, "      connection.setAutoCommit(false);");
		line(builder, "      try {");
		line(builder, "        new JdbcTreeStagingLoader(connection, schema, plan).load();");
		line(builder, "      } catch (Exception e) {");
		line(builder, "        connection.rollback();");
		line(builder, "        throw e;");
		line(builder, "      }");
		line(builder, "    }");
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

	private void addStagingIndexes(Table table, LoadDataSet dataSet) {
		List<String> businessKey = existingStagingColumns(dataSet,
				dataSet.getSourceBusinessKey());
		if (dataSet.getParentDataSetId() == null) {
			List<String> pending = new ArrayList<>();
			pending.add("SQLAPP_LOAD_STATUS");
			pending.addAll(businessKey);
			addIndex(table, indexName(dataSet, "PENDING"), pending);
		}
		addIndex(table, indexName(dataSet, "KEY"), businessKey);

		List<String> parentKey = existingStagingColumns(dataSet,
				dataSet.getParentJoinKeys().stream()
						.map(key -> key.getChildStagingColumn()).toList());
		if (!parentKey.isEmpty() && !startsWithIgnoreCase(businessKey, parentKey)) {
			addIndex(table, indexName(dataSet, "PARENT"), parentKey);
		}
	}

	private List<String> existingStagingColumns(LoadDataSet dataSet,
			List<String> candidates) {
		Set<String> staging = distinctStagingFields(dataSet).stream()
				.map(field -> field.getStagingColumn().toLowerCase(Locale.ROOT))
				.collect(java.util.stream.Collectors.toSet());
		return candidates.stream().filter(name -> name != null
				&& staging.contains(name.toLowerCase(Locale.ROOT))).distinct().toList();
	}

	private void addIndex(Table table, String name, List<String> columns) {
		if (columns.isEmpty()) {
			return;
		}
		Index index = new Index(name);
		columns.forEach(column -> index.getColumns().add(column));
		table.getIndexes().add(index);
	}

	private boolean startsWithIgnoreCase(List<String> columns, List<String> prefix) {
		if (columns.size() < prefix.size()) {
			return false;
		}
		for (int i = 0; i < prefix.size(); i++) {
			if (!columns.get(i).equalsIgnoreCase(prefix.get(i))) {
				return false;
			}
		}
		return true;
	}

	private String indexName(LoadDataSet dataSet, String purpose) {
		String value = "IX_" + dataSet.getStagingTable() + "_" + purpose;
		return value.length() <= 60 ? value
				: value.substring(0, 51) + "_" + String.format(Locale.ROOT, "%08X", value.hashCode());
	}

	private String yaml(String value) {
		return "\""
				+ (value == null ? ""
						: value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n"))
				+ "\"";
	}

	private void line(StringBuilder builder, String value) {
		builder.append(value).append(System.lineSeparator());
	}
}
