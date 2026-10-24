/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTableFactory;
import com.sqlapp.data.db.sql.AddObjectDetail;
import com.sqlapp.data.db.sql.AddTableObjectDetailFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

public class Db2CreateTableFactory extends AbstractCreateTableFactory<Db2SqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Table table) {
		if (!com.sqlapp.data.db.dialect.db2.Db2Nickname.isNickname(table) && !table.getDefinition().isEmpty()) {
			List<SqlOperation> result = new java.util.ArrayList<>();
			for (String statement : table.getDefinition()) {
				if (statement == null || statement.isBlank() || statement.indexOf('\0') >= 0)
					throw new IllegalArgumentException("Db2 table definition must contain executable statements");
				addSql(result, createSqlBuilder()._add(statement), SqlType.CREATE, table);
			}
			// A full definition owns its columns, constraints and organization. Indexes
			// remain separate Schema objects, as on the modeled CREATE TABLE path.
			addIndexDefinitions(table, result);
			addOtherDefinitions(table, result);
			return result;
		}
		if (!com.sqlapp.data.db.dialect.db2.Db2Nickname.isNickname(table))
			return super.createSql(table);
		var sql = Db2NicknameSql.create(table, this::createSqlBuilder);
		List<SqlOperation> result = new java.util.ArrayList<>();
		for (String statement : sql)
			addSql(result, createSqlBuilder()._add(statement),
					statement.startsWith("ALTER ") ? SqlType.ALTER : SqlType.CREATE, table);
		addOtherDefinitions(table, result);
		return result;
	}

	@Override
	protected void addCreateObject(final Table table, final Db2SqlBuilder builder) {
		builder.create().table().ifNotExists(this.getOptions().isCreateIfNotExists());
		builder.name(table, this.getOptions().isDecorateSchemaName());
	}

	/**
	 * オプションを追加します
	 *
	 * @param table
	 * @param builder
	 */
	@Override
	protected void addOption(final Table table, final Db2SqlBuilder builder) {
		addTableOption(table, builder);
		addPartitionByDefinition(table, builder);
	}

	protected void addTableOption(final Table table, final Db2SqlBuilder builder) {
		addCollateDefinition(table, builder);
		if (table.getTableSpaceName() != null)
			builder.space()._add("IN").space().name(table.getTableSpaceName());
		if (table.getIndexTableSpaceName() != null)
			builder.space()._add("INDEX IN").space().name(table.getIndexTableSpaceName());
		if (table.getLobTableSpaceName() != null)
			builder.space()._add("LONG IN").space().name(table.getLobTableSpaceName());
	}

	@Override
	protected void addIndexDefinitions(final Table table, final List<SqlOperation> result) {
		for (Index index : table.getIndexes()) {
			com.sqlapp.data.db.sql.SqlFactory<Index> factory = getSqlFactoryRegistry().getSqlFactory(index,
					SqlType.CREATE);
			result.addAll(factory.createSql(index));
		}
	}

	@Override
	protected void addOtherDefinitions(final Table table, final List<SqlOperation> result) {
		super.addOtherDefinitions(table, result);
		if (!CommonUtils.isEmpty(table.getRemarks())) {
			Db2SqlBuilder builder = createSqlBuilder();
			builder.comment().on().table().name(table, getOptions().isDecorateSchemaName()).is().space()
					.sqlChar(table.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, table);
		}
		for (Column column : table.getColumns()) {
			if (CommonUtils.isEmpty(column.getRemarks()))
				continue;
			Db2SqlBuilder builder = createSqlBuilder();
			builder.comment().on().column().space().name(table, getOptions().isDecorateSchemaName());
			builder._add(".").name(column.getName()).is().space().sqlChar(column.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, column);
		}
	}

	/**
	 * Collate定義を追加します
	 *
	 * @param table
	 * @param builder
	 */
	protected void addCollateDefinition(final Table table, final Db2SqlBuilder builder) {
		if (!CommonUtils.isEmpty(table.getCollation())) {
			throw new IllegalArgumentException("Db2 does not support a table-level COLLATE clause");
		}
	}

	/**
	 * COMMENT定義を追加します
	 *
	 * @param table
	 * @param builder
	 */
	protected void addRemarkDefinition(final Table table, final Db2SqlBuilder builder) {
		// Comments are emitted by addOtherDefinitions as COMMENT ON statements.
	}

	/**
	 * AUTO_INCREMENTの初期値を設定DDLを取得します
	 *
	 * @param colDiff
	 * @param builder
	 */
	protected void addAutoIncrementDefinition(final Table table, final Db2SqlBuilder builder) {
		// Db2 identity start/increment belong to each column's identity clause.
	}

	/**
	 * Partition定義を追加します
	 *
	 * @param table
	 * @param builder
	 */
	protected void addPartitionByDefinition(final Table table, final Db2SqlBuilder builder) {
		if (table.getPartitioning() != null) {
			final AddObjectDetail<Partitioning, AbstractSqlBuilder<?>> addObjectDetail = getAddObjectDetail(
					table.getPartitioning(), SqlType.CREATE);
			addObjectDetail.addObjectDetail(table.getPartitioning(), builder);
		}
	}

	@Override
	protected void addConstraintDefinitions(final Table table, final Db2SqlBuilder builder) {
		super.addConstraintDefinitions(table, builder);
	}

	/**
	 * インデックスを追加します
	 *
	 * @param index
	 * @param builder
	 */
	@Override
	protected void addIndexDefinitions(final Table table, final Db2SqlBuilder builder) {
		// Db2 indexes are separate CREATE INDEX statements, never inline table
		// elements.
	}

	/**
	 * インデックスを追加します
	 *
	 * @param index
	 * @param builder
	 */
	protected void addIndexDefinition(final Index index, final Db2SqlBuilder builder) {
		final AddTableObjectDetailFactory<Index, Db2SqlBuilder> indexOperation = this
				.getAddTableObjectDetailOperationFactory(index);
		if (indexOperation != null) {
			builder.lineBreak().comma();
			indexOperation.addObjectDetail(index, null, builder);
		}
	}

}
