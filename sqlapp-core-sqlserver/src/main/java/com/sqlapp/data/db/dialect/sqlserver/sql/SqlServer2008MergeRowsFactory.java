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

import java.util.Set;

import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractMergeRowsFactory;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ColumnSelectionStrategy;
import com.sqlapp.data.schemas.Table;

public class SqlServer2008MergeRowsFactory extends AbstractMergeRowsFactory<SqlServerSqlBuilder> {

	@Override
	protected void addMergeTableWhenNotMatchedBySource(final Table obj, final String targetTableAlias,
			final String sourceTableAlias, final Set<Set<Column>> keyColumnsSet, final SqlServerSqlBuilder builder) {
		if (this.getTableOptions().getMergeRowsWithDelete().test(obj)) {
			builder.lineBreak();
			builder.when().not().matched().by().source();
			builder.indent(() -> {
				builder.lineBreak();
				builder.then().delete();
			});
		}
	}

	@Override
	protected void addWhenNotMatched(final Table obj, final String targetTableAlias, final String sourceTableAlias,
			final SqlServerSqlBuilder builder) {
		builder.when().not().matched().by().target();
	}

	@Override
	protected void addMergeTableAfter(final Table obj, String targetTableAlias, final String sourceTableAlias,
			final SqlServerSqlBuilder builder) {
		ColumnSelectionStrategy returningColumnStrategy = this.getTableOptions().getReturningColumnStrategy()
				.apply(obj);
		final Set<Column> columns = returningColumnStrategy.getKeyColumns(obj);
		builder.lineBreak();
		builder.output();
		int i = 0;
		for (Column column : columns) {
			if (column.isIdentity()) {
				builder.comma(i > 0).space();
				builder.coalesce(() -> {
					builder.name("INSERTED.", column);
					builder.comma().space();
					builder.name(sourceTableAlias + ".", column);
				});
			} else {
				builder.name(sourceTableAlias + ".", column);
			}
			i++;
		}
		builder.comma().name("$action");
		builder.semicolon();
	}
}
