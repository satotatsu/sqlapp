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
		super.addRenameOrAlterColumn(originalTable, table, diff, result);
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
		if (column.getCheckConstraint() != null)
			dropColumnCheck(column, table, result);
		super.addDeleteColumn(originalTable, table, diff, result);
	}

}
