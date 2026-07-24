/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.normalization;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.properties.OutputDirectoryProperty;
import com.sqlapp.data.db.command.properties.TargetFileProperty;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.AbstractColumnConstraint;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.RepeatColumn;
import com.sqlapp.data.schemas.RepeatColumnClusterBuilder;
import com.sqlapp.data.schemas.RepeatColumnClusterBuilder.RepeatColumnCluster;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.YamlConverter;

import lombok.Getter;
import lombok.Setter;

/**
 * Converts repeating column groups in a schema XML document to first normal
 * form.
 *
 * <p>
 * Row data conversion is not supported. A table containing row data is rejected
 * when it has a normalizable repeating column group.
 * </p>
 */
@Getter
@Setter
public class FirstNormalFormCommand extends AbstractCommand implements TargetFileProperty, OutputDirectoryProperty {

	/** Input schema XML file. */
	private File targetFile;

	/** Output directory. */
	private File outputDirectory = new File("./");

	/** Determines the name of the sequence key column added to a child table. */
	private Function<Table, String> childKeyColumnNameStrategy = table -> "ROW_NO";

	/** Determines the child table name from the source table and cluster number. */
	private BiFunction<Table, Integer, String> childTableNameStrategy = (table,
			clusterNumber) -> table.getName() + "_DETAIL_" + clusterNumber;

	/** Minimum number of repeating column types required to create a child table. */
	private int minimumColumnCount = 2;

	/** Whether to write the normalization mapping log. */
	private boolean normalizationLogEnabled = true;

	/** Normalization log directory. Uses outputDirectory when null. */
	private File normalizationLogDirectory;

	/** Normalization log file name. Derived from targetFile when null. */
	private String normalizationLogFileName;

	@Override
	protected void doRun() {
		validateProperties();
		execute(() -> {
			DbCommonObject<?> root = SchemaUtils.readXml(targetFile);
			Map<String, Object> normalizationLog = normalize(root);
			File outputFile = new File(outputDirectory, targetFile.getName());
			if (targetFile.getCanonicalFile().equals(outputFile.getCanonicalFile())) {
				throw new CommandException("Input and output files must be different: " + outputFile);
			}
			if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
				throw new CommandException("Failed to create output directory: " + outputDirectory);
			}
			root.writeXml(outputFile);
			info("Output normalized schema XML: " + outputFile.getAbsolutePath());
			if (normalizationLogEnabled) {
				writeNormalizationLog(normalizationLog);
			}
		});
	}

	private void validateProperties() {
		if (targetFile == null) {
			throw new CommandException("targetFile is required.");
		}
		if (!targetFile.isFile()) {
			throw new CommandException("targetFile does not exist or is not a file: " + targetFile);
		}
		if (outputDirectory == null) {
			throw new CommandException("outputDirectory is required.");
		}
		if (childKeyColumnNameStrategy == null) {
			throw new CommandException("childKeyColumnNameStrategy is required.");
		}
		if (childTableNameStrategy == null) {
			throw new CommandException("childTableNameStrategy is required.");
		}
		if (minimumColumnCount < 1) {
			throw new CommandException("minimumColumnCount must be greater than or equal to 1.");
		}
	}

	private Map<String, Object> normalize(DbCommonObject<?> root) throws IOException, XMLStreamException {
		Map<String, Object> log = linkedMap();
		log.put("formatVersion", 1);
		log.put("source", mapOf("file", targetFile.getName(), "rootType", root.getClass().getSimpleName()));
		log.put("configuration",
				mapOf("minimumColumnCount", minimumColumnCount, "childKeyColumn",
						mapOf("resolvedPerGeneratedTable", true)));
		List<Map<String, Object>> tableLogs = new ArrayList<>();
		log.put("tables", tableLogs);
		for (Table table : new ArrayList<>(SchemaUtils.toTables(root))) {
			List<RepeatColumnCluster> clusters = RepeatColumnClusterBuilder.of(table)
					.minimumColumnCount(minimumColumnCount).build();
			if (clusters.isEmpty()) {
				continue;
			}
			UniqueConstraint primaryKey = table.getPrimaryKeyConstraint();
			if (primaryKey == null) {
				info("Skip normalization because the table has no primary key: " + table.getName());
				tableLogs.add(mapOf("sourceTable", sourceTableLog(table), "result", "skipped", "reason",
						mapOf("code", "NO_PRIMARY_KEY",
								"message", "A primary key is required to generate the child-table relationship.")));
				continue;
			}
			if (!table.getRows().isEmpty()) {
				throw new CommandException("Row data normalization is not supported: table=" + table.getName());
			}
			Map<String, Object> tableLog = mapOf("sourceTable", sourceTableLog(table), "result", "normalized");
			List<Map<String, Object>> generatedTableLogs = new ArrayList<>();
			tableLog.put("generatedTables", generatedTableLogs);
			tableLogs.add(tableLog);
			int clusterNumber = 0;
			for (RepeatColumnCluster cluster : clusters) {
				clusterNumber++;
				validateRemovedColumnReferences(table, cluster);
				Table childTable = createChildTable(table, primaryKey, cluster, clusterNumber);
				generatedTableLogs.add(generatedTableLog(table, childTable, primaryKey, cluster));
				removeRepeatingColumns(table, cluster);
			}
		}
		return log;
	}

	private Table createChildTable(Table sourceTable, UniqueConstraint primaryKey, RepeatColumnCluster cluster,
			int clusterNumber) {
		String childTableName = requireName(childTableNameStrategy.apply(sourceTable, clusterNumber),
				"childTableNameStrategy");
		if (sourceTable.getParent().contains(childTableName)) {
			throw new CommandException("Child table already exists: " + childTableName);
		}
		String childKeyColumnName = requireName(childKeyColumnNameStrategy.apply(sourceTable),
				"childKeyColumnNameStrategy");

		Table childTable = new Table(childTableName);
		List<Column> childPrimaryKeyColumns = new ArrayList<>();
		List<Column> parentPrimaryKeyColumns = primaryKey.getColumns().toColumns();
		for (Column parentColumn : parentPrimaryKeyColumns) {
			Column childColumn = parentColumn.clone();
			childColumn.setIdentity(false);
			childColumn.setDefaultValue(null);
			childTable.getColumns().add(childColumn);
			childPrimaryKeyColumns.add(childColumn);
		}
		if (childTable.getColumns().contains(childKeyColumnName)) {
			throw new CommandException(
					"Child key column conflicts with a source primary key: table=" + sourceTable.getName()
							+ ", column=" + childKeyColumnName);
		}
		Column childKeyColumn = new Column(childKeyColumnName).setDataType(DataType.INT).setNotNull(true);
		childTable.getColumns().add(childKeyColumn);
		childPrimaryKeyColumns.add(childKeyColumn);

		for (RepeatColumn repeatColumn : cluster) {
			String columnName = requireName(repeatColumn.getBaseName(), "repeat column base name");
			if (childTable.getColumns().contains(columnName)) {
				throw new CommandException("Normalized column name conflicts in child table: table=" + childTableName
						+ ", column=" + columnName);
			}
			Column childColumn = repeatColumn.firstColumn().clone();
			childColumn.setName(columnName);
			childTable.getColumns().add(childColumn);
		}

		sourceTable.getParent().add(childTable);
		childTable.setPrimaryKey("PK_" + childTableName, childPrimaryKeyColumns.toArray(Column[]::new));
		Column[] foreignKeyColumns = childPrimaryKeyColumns.subList(0, parentPrimaryKeyColumns.size())
				.toArray(Column[]::new);
		childTable.getConstraints().addForeignKeyConstraint("FK_" + childTableName + "_" + sourceTable.getName(),
				foreignKeyColumns, parentPrimaryKeyColumns.toArray(Column[]::new));
		return childTable;
	}

	private Map<String, Object> sourceTableLog(Table table) {
		Map<String, Object> result = linkedMap();
		result.put("catalog", table.getCatalogName());
		result.put("schema", table.getSchemaName());
		result.put("name", table.getName());
		UniqueConstraint primaryKey = table.getPrimaryKeyConstraint();
		if (primaryKey != null) {
			result.put("primaryKey", mapOf("name", primaryKey.getName(), "columns",
					primaryKey.getColumns().toColumns().stream().map(Column::getName).toList()));
		}
		return result;
	}

	private Map<String, Object> generatedTableLog(Table sourceTable, Table childTable, UniqueConstraint sourcePrimaryKey,
			RepeatColumnCluster cluster) {
		List<String> parentColumns = sourcePrimaryKey.getColumns().toColumns().stream().map(Column::getName).toList();
		String childKeyName = childKeyColumnNameStrategy.apply(sourceTable);
		Map<String, Object> result = linkedMap();
		result.put("name", childTable.getName());
		result.put("purpose", "Replaces repeating numbered columns from " + qualifiedName(sourceTable) + ".");
		result.put("keyMapping", mapOf("parentColumns", parentColumns, "sequenceColumn",
				mapOf("name", childKeyName, "dataType", "INT", "notNull", true)));
		result.put("primaryKey", mapOf("name", childTable.getPrimaryKeyConstraint().getName(), "columns",
				childTable.getPrimaryKeyConstraint().getColumns().toColumns().stream().map(Column::getName).toList()));
		var foreignKey = childTable.getConstraints().getForeignKeyConstraints().getFirst();
		result.put("foreignKey",
				mapOf("name", foreignKey.getName(), "sourceColumns", parentColumns, "targetTable",
						qualifiedName(sourceTable), "targetColumns", parentColumns));
		List<Map<String, Object>> columnMappings = new ArrayList<>();
		for (RepeatColumn repeatColumn : cluster) {
			List<Map<String, Object>> sourceColumns = new ArrayList<>();
			repeatColumn.getColumns()
					.forEach((index, column) -> sourceColumns.add(mapOf("index", index, "column", column.getName())));
			Column targetColumn = childTable.getColumns().get(repeatColumn.getBaseName());
			Map<String, Object> columnMapping = mapOf("targetColumn", targetColumn.getName(), "dataType",
					targetColumn.getDataType().name());
			if (targetColumn.getLength() != null) {
				columnMapping.put("length", targetColumn.getLength());
			}
			if (targetColumn.getScale() != null) {
				columnMapping.put("scale", targetColumn.getScale());
			}
			columnMapping.put("sourceColumns", sourceColumns);
			columnMappings.add(columnMapping);
		}
		result.put("columnMappings", columnMappings);
		result.put("migrationGuidance",
				mapOf("rowIdentity",
						"One source " + sourceTable.getName() + " row becomes multiple " + childTable.getName()
								+ " rows. " + childKeyName
								+ " corresponds to the numeric suffix of the legacy column.",
						"joinCondition", parentColumns.stream()
								.map(column -> childTable.getName() + "." + column + " = " + sourceTable.getName() + "."
										+ column)
								.toList()));
		return result;
	}

	private void writeNormalizationLog(Map<String, Object> log) {
		File directory = normalizationLogDirectory != null ? normalizationLogDirectory : outputDirectory;
		if (!directory.exists() && !directory.mkdirs()) {
			throw new CommandException("Failed to create normalization log directory: " + directory);
		}
		String fileName = normalizationLogFileName;
		if (fileName == null || fileName.isBlank()) {
			String name = targetFile.getName();
			int extensionIndex = name.lastIndexOf('.');
			if (extensionIndex > 0) {
				name = name.substring(0, extensionIndex);
			}
			fileName = name + "-normalization.yaml";
		}
		File logFile = new File(directory, fileName);
		new YamlConverter().writeJsonValue(logFile, log);
		info("Output normalization log: " + logFile.getAbsolutePath());
	}

	private String qualifiedName(Table table) {
		if (table.getSchemaName() == null || table.getSchemaName().isBlank()) {
			return table.getName();
		}
		return table.getSchemaName() + "." + table.getName();
	}

	private Map<String, Object> linkedMap() {
		return new LinkedHashMap<>();
	}

	private Map<String, Object> mapOf(Object... values) {
		Map<String, Object> map = linkedMap();
		for (int i = 0; i < values.length; i += 2) {
			map.put((String) values[i], values[i + 1]);
		}
		return map;
	}

	private void validateRemovedColumnReferences(Table table, RepeatColumnCluster cluster) {
		Set<String> removedColumnNames = new HashSet<>();
		for (RepeatColumn repeatColumn : cluster) {
			repeatColumn.getColumns().values().forEach(column -> removedColumnNames.add(column.getName()));
		}
		table.getConstraints().forEach(constraint -> {
			if (constraint instanceof AbstractColumnConstraint<?> columnConstraint
					&& referencesAny(columnConstraint.getColumns(), removedColumnNames)) {
				throw new CommandException("A constraint references a repeating column: table=" + table.getName()
						+ ", constraint=" + constraint.getName());
			}
		});
		for (Index index : table.getIndexes()) {
			if (referencesAnyReference(index.getColumns(), removedColumnNames)
					|| referencesAnyReference(index.getIncludes(), removedColumnNames)) {
				throw new CommandException("An index references a repeating column: table=" + table.getName()
						+ ", index=" + index.getName());
			}
		}
	}

	private boolean referencesAny(List<Column> columns, Set<String> names) {
		return columns.stream().anyMatch(column -> names.contains(column.getName()));
	}

	private boolean referencesAnyReference(Iterable<ReferenceColumn> columns, Set<String> names) {
		for (ReferenceColumn column : columns) {
			if (names.contains(column.getName())) {
				return true;
			}
		}
		return false;
	}

	private void removeRepeatingColumns(Table table, RepeatColumnCluster cluster) {
		for (RepeatColumn repeatColumn : cluster) {
			new ArrayList<>(repeatColumn.getColumns().values()).forEach(table.getColumns()::remove);
		}
	}

	private String requireName(String value, String property) {
		if (value == null || value.isBlank()) {
			throw new CommandException(property + " returned an empty name.");
		}
		return value;
	}
}
