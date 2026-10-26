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

package com.sqlapp.data.db.dialect.sqlite.sql;

import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTemporaryTableFactory;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import java.util.List;
import java.util.Objects;

/**
 * CREATE TEMPORARY TABLE
 * 
 * @author satoh
 * 
 */
public class SqliteCreateTemporaryTableFactory extends AbstractCreateTemporaryTableFactory<SqliteSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(final Table source) {
		String prefix = getTableOptions().getTemporaryTableNamePrefix().apply(source);
		String suffix = getTableOptions().getTemporaryTableNameSuffix().apply(source);
		prefix = prefix == null ? "" : prefix;
		suffix = suffix == null ? "" : suffix;
		Table temporary = source.clone();
		temporary.setName(prefix + source.getName() + suffix);
		new Schema("temp").getTables().add(temporary);
		for (var index : temporary.getIndexes())
			index.setName(prefix + index.getName() + suffix);
		var sourceKeys = source.getConstraints().getForeignKeyConstraints();
		var temporaryKeys = temporary.getConstraints().getForeignKeyConstraints();
		for (int i = 0; i < temporaryKeys.size(); i++) {
			var key = temporaryKeys.get(i);
			var originalKey = sourceKeys.get(i);
			String sourceSchema = source.getSchemaName();
			String relatedSchema = originalKey.getRelatedTableSchemaName();
			boolean self = source.getName().equalsIgnoreCase(originalKey.getRelatedTableName())
					&& (Objects.equals(sourceSchema, relatedSchema)
							|| sourceSchema != null && sourceSchema.equalsIgnoreCase(relatedSchema));
			if (!self && !"temp".equalsIgnoreCase(relatedSchema))
				throw new IllegalArgumentException(
						"SQLite temporary foreign key requires an explicit temp parent: " + key.getRelatedTableName());
			String parent = self ? temporary.getName() : key.getRelatedTableName();
			Column[] columns = key.getRelatedColumns().stream()
					.map(column -> new Column(column.getName()).setSchemaName("temp").setTableName(parent))
					.toArray(Column[]::new);
			Table parentModel = new Table(parent).setSchemaName("temp");
			for (Column column : columns)
				parentModel.getColumns().add(column);
			key.getRelatedColumns().clear();
			key.setRelatedTableName(parent);
			key.setRelatedTableSchemaName("temp");
			key.getRelatedColumns().addAll(columns);
		}
		var factory = new SqliteCreateTableFactory() {
			@Override
			protected void addCreateObject(Table table, SqliteSqlBuilder builder) {
				SqliteCreateTemporaryTableFactory.this.addCreateObject(source, builder);
			}

			@Override
			protected void addOption(Table table, SqliteSqlBuilder builder) {
				SqliteCreateTemporaryTableFactory.this.addOption(table, builder);
			}
		};
		factory.setSqlFactoryRegistry(getSqlFactoryRegistry());
		factory.setDialect(getDialect());
		factory.setOptions(getOptions());
		factory.setTableOptions(getTableOptions());
		var operations = factory.createSql(temporary);
		operations.set(0, createOperation(operations.get(0).getSqlText(), SqlType.CREATE_TEMPORARY, source, temporary));
		return operations;
	}

	@Override
	protected void addCreateObject(final Table obj, final SqliteSqlBuilder builder) {
		String prefix = this.getTableOptions().getTemporaryTableNamePrefix().apply(obj);
		String suffix = this.getTableOptions().getTemporaryTableNameSuffix().apply(obj);
		builder.create().temporary().table();
		builder.space()._add(
				this.getDialect().getTemporaryTableName(obj, prefix, suffix, this.getOptions().isDecorateSchemaName()));
	}

	@Override
	protected void addOption(final Table table, final SqliteSqlBuilder builder) {
		new SqliteCreateTableFactory().addOption(table, builder);
	}
}
