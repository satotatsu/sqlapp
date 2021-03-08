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
		builder.lineBreak()._add("(");
		builder.appendIndent(1);
		for (int i = 0; i < columns.size(); i++) {
			final Column column = columns.get(i);
			if (column.isIdentity()) {
				final Dialect dialect = builder.getDialect();
				if (!CommonUtils.isEmpty(dialect.getIdentityInsertString())) {
					builder.lineBreak();
					builder.comma(i > 0).space(2, i == 0);
					builder._add(dialect.getIdentityInsertString());
				}
			} else {
				if (!this.isFormulaColumn(column)) {
					builder.lineBreak();
					builder.comma(i > 0).space(2, i == 0);
					addColumnDefinition(column, builder);
				}
			}
		}
		builder.appendIndent(-1);
		builder.lineBreak()._add(")");
		addSql(sqlList, builder, SqlType.INSERT, table);
		return sqlList;
	}

	protected void addInsertIntoTable(final Table obj, final S builder) {
		builder.insert().into().space();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
		builder.space().lineBreak()._add("(");
		int i=0;
		builder.appendIndent(+1);
		for(final Column column:obj.getColumns()){
			if (!isFormulaColumn(column)) {
				builder.lineBreak().comma(i>0).space(2, i == 0);
				builder.name(column);
				i++;
			}
		}
		builder.appendIndent(-1);
		builder.lineBreak()._add(")").lineBreak().values();
	}


}
