/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ColumnCollection;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * INSERT ROW生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractInsertRowFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractRowFactory<S> {

	@Override
	protected List<SqlOperation> getOperations(final Row row) {
		final List<SqlOperation> sqlList = list();
		final Table table = row.getTable();
		final ColumnCollection columns = table.getColumns();
		final S builder = createSqlBuilder();
		addInsertIntoTable(table, row, builder);
		builder.lineBreak();
		builder.brackets(()->{
			builder.indent(()->{
				final boolean[] first=new boolean[]{true};
				final int[] i=new int[1];
				for (final Column column:columns) {
					if (!isInsertable(column)) {
						continue;
					}
					final String def=this.getValueDefinitionForInsert(row, column);
					builder.$if(def!=null, ()->{
						if (!this.isFormulaColumn(column)) {
							builder.lineBreak();
							builder.comma(!first[0]).space(2, !first[0]);
							builder._add(def);
							first[0]=false;
							i[0]++;
						}
					});
				}
			});
			builder.lineBreak();
		});
		addSql(sqlList, builder, SqlType.INSERT_ROW, row);
		return sqlList;
	}
}
