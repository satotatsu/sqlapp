/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTemporaryTableFactory;
import com.sqlapp.data.schemas.Table;

/**
 * CREATE TEMPORARY TABLE
 * 
 * @author satoh
 * 
 */
public class Firebird20CreateTemporaryTableFactory extends AbstractCreateTemporaryTableFactory<FirebirdSqlBuilder> {
	@Override
	protected void addConstraintDefinitions(Table table, FirebirdSqlBuilder builder) {
		Table physical = physicalTable(table);
		for (var foreign : table.getConstraints().getForeignKeyConstraints()) {
			Table parent = foreign.getRelatedTable();
			if (parent == null || (parent.getTableType() != Table.TableType.Temporary
					&& !parent.getSpecifics().containsKey("firebird.onCommit")))
				throw new IllegalArgumentException("Temporary foreign keys require a resolved temporary parent table");
			Table physicalParent = physicalTable(parent);
			var resolved = (com.sqlapp.data.schemas.ForeignKeyConstraint) physical.getConstraints()
					.get(foreign.getName());
			java.util.List<com.sqlapp.data.schemas.Column> columns = new java.util.ArrayList<>();
			for (var column : foreign.getRelatedColumns()) {
				var target = physicalParent.getColumns().get(column.getName());
				if (target == null)
					throw new IllegalArgumentException("Foreign key parent column does not exist: " + column.getName());
				columns.add(target);
			}
			resolved.getRelatedColumns().clear();
			resolved.getRelatedColumns().addAll(columns.toArray(com.sqlapp.data.schemas.Column[]::new));
			resolved.setRelatedTableName(physicalParent.getName());
		}
		for (var constraint : physical.getConstraints())
			addConstraintDefinition(constraint, builder);
	}

	private Table physicalTable(Table table) {
		String prefix = getTableOptions().getTemporaryTableNamePrefix().apply(table);
		String suffix = getTableOptions().getTemporaryTableNameSuffix().apply(table);
		return table.clone().setName((prefix == null ? "" : prefix) + table.getName() + (suffix == null ? "" : suffix));
	}

	@Override
	protected void addIndexDefinitions(Table table, java.util.List<com.sqlapp.data.db.sql.SqlOperation> result) {
		Table physical = physicalTable(table);
		for (var index : physical.getIndexes())
			result.addAll(getSqlFactoryRegistry().createSql(index, com.sqlapp.data.db.sql.SqlType.CREATE));
	}

	@Override
	protected void addOtherDefinitions(Table table, java.util.List<com.sqlapp.data.db.sql.SqlOperation> result) {
		Table physical = physicalTable(table);
		if (physical.getRemarks() != null)
			result.addAll(getSqlFactoryRegistry().createSql(physical, com.sqlapp.data.db.sql.SqlType.SET_COMMENT));
		for (var column : physical.getColumns())
			if (column.getRemarks() != null)
				result.addAll(getSqlFactoryRegistry().createSql(column, com.sqlapp.data.db.sql.SqlType.SET_COMMENT));
	}

	@Override
	public java.util.List<com.sqlapp.data.db.sql.SqlOperation> createSql(Table table) {
		FirebirdConstraintSql.validateTable(table);
		var result = super.createSql(table);
		if (table.getSpecifics().containsKey("firebird.sqlSecurity")) {
			String name = getDialect().getTemporaryTableName(table,
					getTableOptions().getTemporaryTableNamePrefix().apply(table),
					getTableOptions().getTemporaryTableNameSuffix().apply(table), getOptions().isDecorateSchemaName());
			var builder = createSqlBuilder()._add("ALTER TABLE ")._add(name)._add(" ALTER");
			FirebirdRoutineSql.addSecurity(table.getSpecifics(), builder);
			addSql(result, builder, com.sqlapp.data.db.sql.SqlType.ALTER, table);
		}
		return result;
	}

	@Override
	protected void addCreateObject(final Table obj, final FirebirdSqlBuilder builder) {
		FirebirdTemporaryTableSql.requireSupportedDialect(builder);
		String prefix = this.getTableOptions().getTemporaryTableNamePrefix().apply(obj);
		String suffix = this.getTableOptions().getTemporaryTableNameSuffix().apply(obj);
		builder.create().global().temporary().table();
		builder.space()._add(
				this.getDialect().getTemporaryTableName(obj, prefix, suffix, this.getOptions().isDecorateSchemaName()));
	}

	/**
	 * オプションを追加します
	 * 
	 * @param table
	 * @param builder
	 */
	@Override
	protected void addOption(final Table table, final FirebirdSqlBuilder builder) {
		FirebirdTemporaryTableSql.addOnCommit(table, getTableOptions().getTempTableOnCommitPreserveRows().test(table),
				builder);
	}

}
