/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver.sql;

import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractAlterTableFactory;
import com.sqlapp.data.db.sql.AddObjectDetail;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Constraint;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Difference;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.Table;

/**
 * SQLServer2005用alterテーブル作成
 * 
 * @author tatsuo satoh
 * 
 */
public class SqlServer2005AlterTableFactory extends AbstractAlterTableFactory<SqlServerSqlBuilder> {
	@Override
	protected void addColumnDefinitions(Map<String, Difference<?>> allDiff, Table originalTable, Table table,
			com.sqlapp.data.schemas.DbObjectDifferenceCollection columns, List<SqlOperation> result) {
		var modified = columns.getList(com.sqlapp.data.schemas.State.Modified);
		for (var deleted : columns.getList(com.sqlapp.data.schemas.State.Deleted)) {
			if (modified.stream().noneMatch(change -> change.getOriginal() == deleted.getOriginal()))
				addDeleteColumn(originalTable, table, deleted, result);
		}
		var changes = columns.getList(com.sqlapp.data.schemas.State.Added, com.sqlapp.data.schemas.State.Modified);
		sortColumnDiff(changes);
		for (var change : changes) {
			if (change.getState() == com.sqlapp.data.schemas.State.Added) {
				if (modified.stream().noneMatch(item -> item.getTarget() == change.getTarget()))
					addAddColumn(originalTable, table, change, result);
			} else {
				addRenameOrAlterColumn(originalTable, table, change, result);
			}
		}
	}

	/** SQL Server omits the optional COLUMN keyword in ADD column syntax. */
	@Override
	protected void addAddColumn(final Table originalTable, final Table table, final DbObjectDifference diff,
			final List<SqlOperation> result) {
		final Column column = diff.getTarget(Column.class);
		final SqlServerSqlBuilder builder = createSqlBuilder();
		builder.alter().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		if (column.getCheckConstraint() != null)
			SqlServerConstraintSupport.addPrefix(column.getCheckConstraint(), builder);
		builder.add();
		builder.name(column);
		builder.space().definition(column, this.getTableOptions().getWithColumnRemarks().test(column));
		add(result, createOperation(builder.toString(), SqlType.ALTER, null, column));
		if (column.getCheckConstraint() != null)
			SqlServerConstraintSupport.restore(column.getCheckConstraint(), createSqlBuilder(),
					getOptions().isDecorateSchemaName(), false, result);
	}

	/**
	 * 制約定義を追加します
	 * 
	 * @param originalTable
	 * @param table
	 * @param consDiff
	 * @param result
	 */
	@Override
	protected void addConstraintDefinitions(Map<String, Difference<?>> allDiff, Table originalTable, Table table,
			List<DbObjectDifference> consDiff, List<SqlOperation> result) {
		Difference<?> tableProp = allDiff.get(SchemaObjectProperties.PARTITIONING.getLabel());
		if (tableProp == null) {
			for (DbObjectDifference diff : consDiff) {
				Constraint originalConstraint = diff.getOriginal(Constraint.class);
				Constraint constraint = diff.getTarget(Constraint.class);
				addConstraintDefinition(originalTable, table, originalConstraint, constraint, diff, result);
			}
		} else {

		}
	}

	/**
	 * Partition定義を追加します
	 * 
	 * @param partitionInfoProp
	 * @param sqlBuilder
	 */
	@Override
	protected void addPartitionDefinition(Map<String, Difference<?>> allDiff, Table originalTable, Table table,
			DbObjectDifference partitioningProp, List<SqlOperation> result) {
		SqlServerSqlBuilder builder = createSqlBuilder();
		builder.alter().table().space().name(table, this.getOptions().isDecorateSchemaName());
		AddObjectDetail<Partitioning, SqlServerSqlBuilder> addObjectDetail = this
				.getAddObjectDetail(table.getPartitioning(), SqlType.CREATE);
		addObjectDetail.addObjectDetail(table.getPartitioning(), builder);
	}

	@Override
	protected void addConstraintDefinition(Table originalTable, Table table, Constraint original, Constraint target,
			DbObjectDifference diff, List<SqlOperation> result) {
		if (diff.getState() == com.sqlapp.data.schemas.State.Modified
				&& (target instanceof com.sqlapp.data.schemas.CheckConstraint
						|| target instanceof com.sqlapp.data.schemas.ForeignKeyConstraint)
				&& SqlServerConstraintSupport.stateOnly(original, target)) {
			SqlServerConstraintSupport.changeState(target, createSqlBuilder(), getOptions().isDecorateSchemaName(),
					result);
			return;
		}
		super.addConstraintDefinition(originalTable, table, original, target, diff, result);
	}

	@Override
	protected void addCreateConstraintDefinition(Table originalTable, Table table, Constraint originalConstraint,
			Constraint constraint, DbObjectDifference diff, List<SqlOperation> result) {
		if (constraint instanceof com.sqlapp.data.schemas.CheckConstraint
				|| constraint instanceof com.sqlapp.data.schemas.ForeignKeyConstraint) {
			result.addAll(getSqlFactoryRegistry().createSql(constraint, SqlType.CREATE));
		} else {
			super.addCreateConstraintDefinition(originalTable, table, originalConstraint, constraint, diff, result);
		}
	}

	@Override
	protected void addRenameOrAlterColumn(Table originalTable, Table table, DbObjectDifference diff,
			List<SqlOperation> result) {
		Column original = diff.getOriginal(Column.class), target = diff.getTarget(Column.class);
		if (!java.util.Objects.equals(original.getCheckConstraint(), target.getCheckConstraint())) {
			Column normalized = target.clone();
			normalized.setCheckConstraint(
					original.getCheckConstraint() == null ? null : original.getCheckConstraint().clone());
			if (!original.equals(normalized))
				throw new IllegalArgumentException(
						"Change a column CHECK separately from its column definition: " + target.getName());
			return; // The CHECK is handled below as a named constraint, never ALTER COLUMN ...
					// CHECK.
		}
		if (original.getCheckConstraint() != null)
			throw new IllegalArgumentException(
					"Remove the column CHECK before altering its column definition: " + target.getName());
		boolean defaultChanged = !java.util.Objects.equals(original.getDefaultValue(), target.getDefaultValue())
				|| !com.sqlapp.util.CommonUtils.isEmpty(original.getDefaultValue()) && !java.util.Objects
						.equals(original.getSpecifics().get("default_name"), target.getSpecifics().get("default_name"));
		if (defaultChanged && !com.sqlapp.util.CommonUtils.isEmpty(original.getDefaultValue()))
			dropDefault(original, originalTable, result);
		if (!java.util.Objects.equals(original.getName(), target.getName()))
			addRenameColumn(originalTable, table, original, target, diff, result);
		var changes = diff.toDifference().getChangedProperties(getDialect());
		if (changes.keySet().stream()
				.anyMatch(key -> !key.equals("name") && !key.equals("defaultValue") && !key.equals("default_name")))
			addAlterColumn(originalTable, table, original, target, diff, result);
		if (defaultChanged && !com.sqlapp.util.CommonUtils.isEmpty(target.getDefaultValue())) {
			var builder = createSqlBuilder();
			builder.alter().table().name(table, getOptions().isDecorateSchemaName()).add();
			String name = target.getSpecifics().get("default_name");
			if (name != null && !name.isBlank())
				builder.constraint().name(name);
			builder._add(" DEFAULT ")._add(target.getDefaultValue())._add(" FOR ").name(target);
			add(result, createOperation(builder.toString(), SqlType.ALTER, original, target));
		}
	}

	@Override
	protected void addRenameColumn(Table originalTable, Table table, Column original, Column target,
			DbObjectDifference diff, List<SqlOperation> result) {
		var name = createSqlBuilder().name(originalTable, getOptions().isDecorateSchemaName());
		String qualified = name.toString().trim() + "." + getDialect().quote(original.getName());
		var builder = createSqlBuilder();
		builder._add("EXEC sys.sp_rename ")._add(SqlServerDdlSupport.nchar(qualified))._add(", ")
				._add(SqlServerDdlSupport.nchar(target.getName()))._add(", N'COLUMN'");
		add(result, createOperation(builder.toString(), SqlType.ALTER, original, target));
	}

	private void dropDefault(Column column, Table table, List<SqlOperation> result) {
		var builder = createSqlBuilder();
		String constraint = column.getSpecifics().get("default_name");
		if (constraint != null && !constraint.isBlank()) {
			builder.alter().table().name(table, getOptions().isDecorateSchemaName()).drop().constraint()
					.name(constraint);
		} else {
			// Old Schema XML need not duplicate a database-assigned constraint name.
			String tableName = createSqlBuilder().name(table, getOptions().isDecorateSchemaName()).toString().trim();
			String drop = "ALTER TABLE " + tableName + " DROP CONSTRAINT ";
			builder._add("DECLARE @sqlapp_default_name sysname;\nSELECT @sqlapp_default_name = dc.name ")
					._add("FROM sys.default_constraints dc JOIN sys.columns c ")
					._add("ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id ")
					._add("WHERE dc.parent_object_id = OBJECT_ID(")._add(SqlServerDdlSupport.nchar(tableName))
					._add(") AND c.name = ")._add(SqlServerDdlSupport.nchar(column.getName()))
					._add(";\nIF @sqlapp_default_name IS NULL\n")
					._add("RAISERROR ('Expected DEFAULT constraint was not found', 16, 1);\nELSE\nEXEC (")
					._add(SqlServerDdlSupport.nchar(drop))._add(" + QUOTENAME(@sqlapp_default_name));");
			builder = createSqlBuilder()._add("EXEC (")._add(SqlServerDdlSupport.nchar(builder.toString()))._add(")");
		}
		add(result, createOperation(builder.toString(), SqlType.ALTER, column));
	}

	@Override
	protected void addOtherDefinitions(Map<String, Difference<?>> allDiff, Table originalTable, Table table,
			List<SqlOperation> result) {
		super.addOtherDefinitions(allDiff, originalTable, table, result);
		for (Column column : table.getColumns()) {
			Column original = originalTable.getColumns().get(column.getName());
			if (original == null
					|| java.util.Objects.equals(original.getCheckConstraint(), column.getCheckConstraint()))
				continue;
			if (original.getCheckConstraint() != null && column.getCheckConstraint() != null
					&& SqlServerConstraintSupport.stateOnly(original.getCheckConstraint(),
							column.getCheckConstraint())) {
				SqlServerConstraintSupport.changeState(column.getCheckConstraint(), createSqlBuilder(),
						getOptions().isDecorateSchemaName(), result);
				continue;
			}
			if (original.getCheckConstraint() != null)
				dropColumnCheck(original, table, result);
			if (column.getCheckConstraint() != null)
				result.addAll(getSqlFactoryRegistry().createSql(column.getCheckConstraint(), SqlType.CREATE));
		}
	}

	private void dropColumnCheck(Column column, Table table, List<SqlOperation> result) {
		String name = column.getCheckConstraint().getName();
		if (name == null || name.isBlank())
			throw new IllegalArgumentException(
					"Read the database-assigned CHECK name before changing or dropping column " + column.getName());
		SqlServerSqlBuilder builder = createSqlBuilder();
		builder.alter().table().name(table, getOptions().isDecorateSchemaName()).drop().constraint().space().name(name);
		result.add(createOperation(builder.toString(), SqlType.ALTER, column.getCheckConstraint()));
	}

	@Override
	protected void addDeleteColumn(Table originalTable, Table table, DbObjectDifference diff,
			List<SqlOperation> result) {
		Column column = diff.getOriginal(Column.class);
		if (!com.sqlapp.util.CommonUtils.isEmpty(column.getDefaultValue()))
			dropDefault(column, table, result);
		if (column.getCheckConstraint() != null)
			dropColumnCheck(column, table, result);
		super.addDeleteColumn(originalTable, table, diff, result);
	}

}
