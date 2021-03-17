/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ColumnCollection;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * INSERT生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractInsertTableFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractTableFactory<S> {

	@Override
	public List<SqlOperation> createSql(final Table table) {
		final List<SqlOperation> sqlList = list();
		final ColumnCollection columns = table.getColumns();
		final S builder = createSqlBuilder();
		addInsertIntoTable(table, builder);
		builder.lineBreak();
		builder.brackets(()->{
			builder.indent(()->{
				int i=0;
				for (final Column column:columns) {
					if (!isInsertable(column)) {
						continue;
					}
					if (column.isIdentity()) {
						final Dialect dialect = builder.getDialect();
						if (!CommonUtils.isEmpty(dialect.getIdentityInsertString())) {
							builder.lineBreak();
							builder.comma(i > 0).space(2, i == 0);
							builder._add(dialect.getIdentityInsertString());
							i++;
						}
					} else {
						if (!this.isFormulaColumn(column)) {
							builder.lineBreak();
							builder.comma(i > 0).space(2, i == 0);
							addColumnDefinition(column, builder);
							i++;
						}
					}
				}
			});
			builder.lineBreak();
		});
		addSql(sqlList, builder, SqlType.INSERT, table);
		return sqlList;
	}

	protected void addInsertIntoTable(final Table obj, final S builder) {
		builder.insert().into().space();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
		builder.space().lineBreak();
		builder.brackets(()->{
			builder.indent(()->{
				int i=0;
				for(final Column column:obj.getColumns()){
					if (!isInsertable(column)) {
						continue;
					}
					if (column.isIdentity()) {
						final Dialect dialect = builder.getDialect();
						if (!CommonUtils.isEmpty(dialect.getIdentityInsertString())) {
							builder.lineBreak();
							builder.comma(i > 0).space(2, i == 0);
							builder.name(column);
							i++;
						}
					} else {
						if (!this.isFormulaColumn(column)) {
							builder.lineBreak();
							builder.comma(i > 0).space(2, i == 0);
							builder.name(column);
							i++;
						}
					}
				}
			});
			builder.lineBreak();
		});
		builder.lineBreak().values();
	}
}
