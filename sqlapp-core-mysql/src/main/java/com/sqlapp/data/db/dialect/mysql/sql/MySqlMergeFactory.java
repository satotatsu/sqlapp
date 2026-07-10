/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;
import java.util.Set;

import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractMergeFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ColumnSelectionStrategy;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

public class MySqlMergeFactory extends AbstractMergeFactory<MySqlSqlBuilder> {

	@Override
	public List<SqlOperation> createSql(final Table table) {
		final List<SqlOperation> sqlList = list();
		ColumnSelectionStrategy strategy = this.getTableOptions().getUpdateKeyColumnsMatchingStrategy().apply(table);
		final Set<Column> columns = strategy.getKeyColumns(table);
		final MySqlSqlBuilder builder = createSqlBuilder();
		builder.insert().into().space().name(table, this.getOptions().isDecorateSchemaName());
		builder.lineBreak();
		builder.brackets(true, () -> {
			int i = 0;
			for (final Column column : table.getColumns()) {
				final String def = this.getValueDefinitionForInsert(column);
				if (CommonUtils.isEmpty(def)) {
					continue;
				}
				if (!isFormulaColumn(column)) {
					builder.lineBreak(i > 0);
					builder.comma(i > 0).name(column);
					i++;
				}
			}
		});
		builder.lineBreak();
		builder.values();
		builder.lineBreak();
		builder.brackets(true, () -> {
			int i = 0;
			for (final Column column : table.getColumns()) {
				final String def = this.getValueDefinitionForInsert(column);
				if (CommonUtils.isEmpty(def)) {
					continue;
				}
				if (!isFormulaColumn(column)) {
					builder.lineBreak(i > 0);
					builder.comma(i > 0)._add(def);
					i++;
				}
			}
		});
		builder.lineBreak();
		builder.on().duplicate().key();
		builder.lineBreak();
		builder.update();
		builder.indent(() -> {
			int i = 0;
			for (final Column column : table.getColumns()) {
				if (columns.contains(column)) {
					continue;
				}
				if (isFormulaColumn(column)) {
					continue;
				}
				final String def = this.getValueDefinitionForUpdate(column);
				if (this.isOptimisticLockColumn(column)) {
					if (this.withCoalesceAtUpdate(column)) {
						builder.lineBreak();
						builder.comma(i > 0).name(column).eq().coalesce()._add('(').space().values().space()._add('(');
						builder.name(column).space()._add("), ");
						builder._add(getDefaultValueDefinition(column));
						builder._add(") + 1");
					} else {
						builder.lineBreak();
						builder.comma(i > 0).name(column).eq().coalesce()._add('(');
						builder.name(column).space().comma();
						builder._add(getDefaultValueDefinition(column));
						builder._add(" ) + 1");
					}
					i++;
					continue;
				} else if (this.isUpdatedAtColumn(column)) {
					builder.lineBreak();
					builder.comma(i > 0).name(column).eq().space()._add(def);
					i++;
					continue;
				}
				if (def != null) {
					builder.lineBreak();
					builder.comma(i > 0).name(column).eq().values().brackets(() -> {
						builder.name(column).space();
					});
					i++;
				}
			}
		});
		addSql(sqlList, builder, SqlType.MERGE, table);
		return sqlList;
	}

}
