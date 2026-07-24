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
import java.util.List;
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

	@Override
	protected void doRun() {
		validateProperties();
		execute(() -> {
			DbCommonObject<?> root = SchemaUtils.readXml(targetFile);
			normalize(root);
			File outputFile = new File(outputDirectory, targetFile.getName());
			if (targetFile.getCanonicalFile().equals(outputFile.getCanonicalFile())) {
				throw new CommandException("Input and output files must be different: " + outputFile);
			}
			if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
				throw new CommandException("Failed to create output directory: " + outputDirectory);
			}
			root.writeXml(outputFile);
			info("Output normalized schema XML: " + outputFile.getAbsolutePath());
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

	private void normalize(DbCommonObject<?> root) throws IOException, XMLStreamException {
		for (Table table : new ArrayList<>(SchemaUtils.toTables(root))) {
			List<RepeatColumnCluster> clusters = RepeatColumnClusterBuilder.of(table)
					.minimumColumnCount(minimumColumnCount).build();
			if (clusters.isEmpty()) {
				continue;
			}
			UniqueConstraint primaryKey = table.getPrimaryKeyConstraint();
			if (primaryKey == null) {
				info("Skip normalization because the table has no primary key: " + table.getName());
				continue;
			}
			if (!table.getRows().isEmpty()) {
				throw new CommandException("Row data normalization is not supported: table=" + table.getName());
			}
			int clusterNumber = 0;
			for (RepeatColumnCluster cluster : clusters) {
				clusterNumber++;
				validateRemovedColumnReferences(table, cluster);
				createChildTable(table, primaryKey, cluster, clusterNumber);
				removeRepeatingColumns(table, cluster);
			}
		}
	}

	private void createChildTable(Table sourceTable, UniqueConstraint primaryKey, RepeatColumnCluster cluster,
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
