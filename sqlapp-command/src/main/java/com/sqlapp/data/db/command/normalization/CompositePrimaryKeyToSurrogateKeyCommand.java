/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.normalization;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.properties.OutputDirectoryProperty;
import com.sqlapp.data.db.command.properties.TargetFileProperty;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.AbstractColumnConstraint;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Constraint;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.YamlConverter;

import lombok.Getter;
import lombok.Setter;

/**
 * Replaces composite primary keys and foreign keys that reference them with
 * generated single-column keys.
 */
@Getter
@Setter
public class CompositePrimaryKeyToSurrogateKeyCommand extends AbstractCommand
		implements TargetFileProperty, OutputDirectoryProperty {

	private File targetFile;

	private File outputDirectory = new File("./");

	private Function<Table, String> primaryKeyColumnNameStrategy = table -> "ID";

	private Function<Table, DataType> primaryKeyDataTypeStrategy = table -> DataType.INT;

	/**
	 * Receives the referenced table name and the original local foreign-key
	 * column names.
	 */
	private BiFunction<String, List<String>, String> foreignKeyColumnNameStrategy = (tableName,
			columnNames) -> "PARENT_ID";

	private SurrogateKeyGenerationType generationType = SurrogateKeyGenerationType.IDENTITY;

	private Function<Table, String> sequenceNameStrategy = table -> "SEQ_" + table.getName();

	private boolean conversionLogEnabled = true;

	private File conversionLogDirectory;

	private String conversionLogFileName;

	@Override
	protected void doRun() {
		validateProperties();
		execute(() -> {
			DbCommonObject<?> root = SchemaUtils.readXml(targetFile);
			Map<String, Object> log = transform(root);
			File outputFile = new File(outputDirectory, targetFile.getName());
			if (targetFile.getCanonicalFile().equals(outputFile.getCanonicalFile())) {
				throw new CommandException("Input and output files must be different: " + outputFile);
			}
			ensureDirectory(outputDirectory, "output");
			root.writeXml(outputFile);
			info("Output surrogate-key schema XML: " + outputFile.getAbsolutePath());
			if (conversionLogEnabled) {
				writeLog(log);
			}
		});
	}

	private void validateProperties() {
		if (targetFile == null || !targetFile.isFile()) {
			throw new CommandException("targetFile does not exist or is not a file: " + targetFile);
		}
		if (outputDirectory == null) {
			throw new CommandException("outputDirectory is required.");
		}
		if (primaryKeyColumnNameStrategy == null || primaryKeyDataTypeStrategy == null
				|| foreignKeyColumnNameStrategy == null || generationType == null || sequenceNameStrategy == null) {
			throw new CommandException("Surrogate-key strategies and generationType are required.");
		}
	}

	/**
	 * Transforms an already loaded schema model.
	 *
	 * @param root schema model root
	 * @return mapping information suitable for a YAML migration log
	 */
	public Map<String, Object> transform(DbCommonObject<?> root) {
		List<TablePlan> tablePlans = new ArrayList<>();
		Map<Table, TablePlan> plansByTable = new LinkedHashMap<>();
		for (Table table : SchemaUtils.toTables(root)) {
			UniqueConstraint primaryKey = table.getPrimaryKeyConstraint();
			if (primaryKey != null && primaryKey.getColumns().size() > 1) {
				if (!table.getRows().isEmpty()) {
					throw new CommandException("Row data conversion is not supported: table=" + table.getName());
				}
				TablePlan plan = new TablePlan(table, primaryKey,
						new ArrayList<>(primaryKey.getColumns().toColumns()));
				tablePlans.add(plan);
				plansByTable.put(table, plan);
			}
		}

		List<ForeignKeyPlan> foreignKeyPlans = new ArrayList<>();
		Map<Table, Integer> convertedParentCounts = new LinkedHashMap<>();
		for (Table table : SchemaUtils.toTables(root)) {
			for (ForeignKeyConstraint foreignKey : new ArrayList<>(
					table.getConstraints().getForeignKeyConstraints())) {
				TablePlan parentPlan = plansByTable.get(foreignKey.getRelatedTable());
				if (parentPlan == null) {
					continue;
				}
				List<String> relatedColumnNames = foreignKey.getRelatedColumns().stream()
						.map(item -> item.getName()).toList();
				if (!sameColumnNames(relatedColumnNames, parentPlan.oldPrimaryKeyColumns)) {
					throw new CommandException("A foreign key must reference all columns of the composite primary key: "
							+ "table=" + table.getName() + ", foreignKey=" + foreignKey.getName());
				}
				ForeignKeyPlan plan = new ForeignKeyPlan(table, foreignKey,
						new ArrayList<>(foreignKey.getColumns()), parentPlan);
				foreignKeyPlans.add(plan);
				convertedParentCounts.merge(table, 1, Integer::sum);
			}
		}

		for (ForeignKeyPlan plan : foreignKeyPlans) {
			validateRemovedColumns(plan);
		}
		for (TablePlan plan : tablePlans) {
			createSurrogatePrimaryKey(plan);
		}
		for (ForeignKeyPlan plan : foreignKeyPlans) {
			replaceForeignKey(plan, convertedParentCounts.get(plan.table) > 1);
		}
		for (TablePlan plan : tablePlans) {
			replacePrimaryKey(plan, foreignKeyPlans);
		}
		return createLog(tablePlans, foreignKeyPlans);
	}

	private void createSurrogatePrimaryKey(TablePlan plan) {
		String name = requireName(primaryKeyColumnNameStrategy.apply(plan.table), "primaryKeyColumnNameStrategy");
		if (plan.table.getColumns().contains(name)) {
			throw new CommandException("Surrogate primary-key column already exists: table=" + plan.table.getName()
					+ ", column=" + name);
		}
		DataType dataType = primaryKeyDataTypeStrategy.apply(plan.table);
		if (dataType == null) {
			throw new CommandException("primaryKeyDataTypeStrategy returned null: table=" + plan.table.getName());
		}
		Column column = new Column(name).setDataType(dataType).setNotNull(true);
		if (generationType == SurrogateKeyGenerationType.IDENTITY) {
			column.setIdentity(true);
		} else {
			Schema schema = plan.table.getSchema();
			if (schema == null) {
				throw new CommandException("SEQUENCE generation requires a Schema parent: table=" + plan.table.getName());
			}
			String sequenceName = requireName(sequenceNameStrategy.apply(plan.table), "sequenceNameStrategy");
			Sequence sequence = schema.getSequences().get(sequenceName);
			if (sequence == null) {
				sequence = new Sequence(sequenceName).setDataType(dataType);
				schema.getSequences().add(sequence);
			} else if (sequence.getDataType() != null && sequence.getDataType() != dataType) {
				throw new CommandException("Existing sequence has a different data type: sequence=" + sequenceName
						+ ", expected=" + dataType + ", actual=" + sequence.getDataType());
			}
			column.setSequence(sequence);
		}
		plan.table.getColumns().add(0, column);
		plan.surrogateColumn = column;
	}

	private void replaceForeignKey(ForeignKeyPlan plan, boolean multipleParents) {
		List<String> oldNames = plan.oldColumns.stream().map(Column::getName).toList();
		String requestedName = requireName(
				foreignKeyColumnNameStrategy.apply(plan.parent.table.getName(), oldNames),
				"foreignKeyColumnNameStrategy");
		String name = requestedName;
		if (multipleParents && "PARENT_ID".equalsIgnoreCase(requestedName)) {
			name = plan.parent.table.getName() + "_ID";
		}
		boolean notNull = plan.oldColumns.stream().allMatch(Column::isNotNull);
		for (Column oldColumn : plan.oldColumns) {
			if (!plan.table.getColumns().contains(oldColumn.getName())) {
				continue;
			}
			plan.table.getColumns().remove(oldColumn);
		}
		if (plan.table.getColumns().contains(name)) {
			throw new CommandException("Surrogate foreign-key column already exists: table=" + plan.table.getName()
					+ ", column=" + name);
		}
		Column column = new Column(name).setDataType(plan.parent.surrogateColumn.getDataType()).setNotNull(notNull);
		plan.table.getColumns().add(column);
		plan.table.getConstraints().remove(plan.foreignKey);
		ForeignKeyConstraint replacement = plan.table.getConstraints().addForeignKeyConstraint(plan.foreignKey.getName(),
				column, plan.parent.surrogateColumn);
		replacement.setDeleteRule(plan.foreignKey.getDeleteRule());
		replacement.setUpdateRule(plan.foreignKey.getUpdateRule());
		replacement.setMatchOption(plan.foreignKey.getMatchOption());
		replacement.setVirtual(plan.foreignKey.isVirtual());
		plan.surrogateColumn = column;
	}

	private void replacePrimaryKey(TablePlan plan, List<ForeignKeyPlan> foreignKeyPlans) {
		List<Column> businessKey = new ArrayList<>(plan.oldPrimaryKeyColumns);
		for (ForeignKeyPlan foreignKeyPlan : foreignKeyPlans) {
			if (foreignKeyPlan.table == plan.table
					&& businessKey.containsAll(foreignKeyPlan.oldColumns)) {
				businessKey.removeAll(foreignKeyPlan.oldColumns);
				businessKey.add(0, foreignKeyPlan.surrogateColumn);
			}
		}
		plan.table.getConstraints().remove(plan.oldPrimaryKey);
		String oldPrimaryKeyName = plan.oldPrimaryKey.getName();
		plan.table.setPrimaryKey(oldPrimaryKeyName, plan.surrogateColumn);
		String uniqueName = uniqueConstraintName(plan.table);
		plan.table.getConstraints().addUniqueConstraint(uniqueName, businessKey);
		plan.businessKeyColumns = businessKey;
	}

	private void validateRemovedColumns(ForeignKeyPlan plan) {
		Set<String> names = new HashSet<>(plan.oldColumns.stream().map(Column::getName).toList());
		for (Constraint constraint : plan.table.getConstraints()) {
			if (constraint == plan.foreignKey || constraint == plan.table.getPrimaryKeyConstraint()) {
				continue;
			}
			if (constraint instanceof AbstractColumnConstraint<?> columnConstraint
					&& columnConstraint.getColumns().stream().anyMatch(column -> names.contains(column.getName()))) {
				throw new CommandException("A constraint references a replaced foreign-key column: table="
						+ plan.table.getName() + ", constraint=" + constraint.getName());
			}
		}
		for (Index index : plan.table.getIndexes()) {
			if (index.getColumns().toColumns().stream().anyMatch(column -> names.contains(column.getName()))
					|| index.getIncludes().toColumns().stream().anyMatch(column -> names.contains(column.getName()))) {
				throw new CommandException("An index references a replaced foreign-key column: table="
						+ plan.table.getName() + ", index=" + index.getName());
			}
		}
	}

	private boolean sameColumnNames(List<String> left, List<Column> right) {
		if (left.size() != right.size()) {
			return false;
		}
		// Some schema XML variants omit the related column names when the
		// referenced key can be resolved positionally.
		return java.util.stream.IntStream.range(0, left.size())
				.allMatch(i -> left.get(i) == null || left.get(i).equalsIgnoreCase(right.get(i).getName()));
	}

	private String uniqueConstraintName(Table table) {
		String base = "UK_" + table.getName() + "_1";
		String name = base;
		int suffix = 1;
		while (table.getConstraints().contains(name)) {
			name = base + "_" + (++suffix);
		}
		return name;
	}

	private Map<String, Object> createLog(List<TablePlan> tablePlans, List<ForeignKeyPlan> foreignKeyPlans) {
		Map<String, Object> log = mapOf("formatVersion", 1, "generationType", generationType.name());
		List<Map<String, Object>> tables = new ArrayList<>();
		log.put("tables", tables);
		for (TablePlan plan : tablePlans) {
			List<Map<String, Object>> foreignKeys = foreignKeyPlans.stream()
					.filter(item -> item.table == plan.table)
					.map(item -> mapOf("name", item.foreignKey.getName(), "oldColumns",
							item.oldColumns.stream().map(Column::getName).toList(), "newColumn",
							item.surrogateColumn.getName(), "referencedTable", item.parent.table.getName(),
							"referencedColumn", item.parent.surrogateColumn.getName()))
					.toList();
			tables.add(mapOf("table", plan.table.getName(), "oldPrimaryKey",
					plan.oldPrimaryKeyColumns.stream().map(Column::getName).toList(), "newPrimaryKey",
					mapOf("column", plan.surrogateColumn.getName(), "dataType",
							plan.surrogateColumn.getDataType().name()),
					"businessKey", plan.businessKeyColumns.stream().map(Column::getName).toList(),
					"foreignKeyReplacements", foreignKeys));
		}
		return log;
	}

	private void writeLog(Map<String, Object> log) {
		File directory = conversionLogDirectory != null ? conversionLogDirectory : outputDirectory;
		ensureDirectory(directory, "conversion log");
		String fileName = conversionLogFileName;
		if (fileName == null || fileName.isBlank()) {
			String name = targetFile.getName();
			int extensionIndex = name.lastIndexOf('.');
			fileName = (extensionIndex > 0 ? name.substring(0, extensionIndex) : name)
					+ "-surrogate-key.yaml";
		}
		File file = new File(directory, fileName);
		new YamlConverter().writeJsonValue(file, log);
		info("Output surrogate-key conversion log: " + file.getAbsolutePath());
	}

	private void ensureDirectory(File directory, String type) {
		if (!directory.exists() && !directory.mkdirs()) {
			throw new CommandException("Failed to create " + type + " directory: " + directory);
		}
	}

	private String requireName(String name, String property) {
		if (name == null || name.isBlank()) {
			throw new CommandException(property + " must return a non-empty name.");
		}
		return name;
	}

	private Map<String, Object> mapOf(Object... values) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < values.length; i += 2) {
			map.put((String) values[i], values[i + 1]);
		}
		return map;
	}

	private static final class TablePlan {
		private final Table table;
		private final UniqueConstraint oldPrimaryKey;
		private final List<Column> oldPrimaryKeyColumns;
		private Column surrogateColumn;
		private List<Column> businessKeyColumns;

		private TablePlan(Table table, UniqueConstraint oldPrimaryKey, List<Column> oldPrimaryKeyColumns) {
			this.table = table;
			this.oldPrimaryKey = oldPrimaryKey;
			this.oldPrimaryKeyColumns = oldPrimaryKeyColumns;
		}
	}

	private static final class ForeignKeyPlan {
		private final Table table;
		private final ForeignKeyConstraint foreignKey;
		private final List<Column> oldColumns;
		private final TablePlan parent;
		private Column surrogateColumn;

		private ForeignKeyPlan(Table table, ForeignKeyConstraint foreignKey, List<Column> oldColumns,
				TablePlan parent) {
			this.table = table;
			this.foreignKey = foreignKey;
			this.oldColumns = oldColumns;
			this.parent = parent;
		}
	}
}
