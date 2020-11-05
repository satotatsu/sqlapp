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
		List<SqlOperation> sqlList = list();
		Table table = row.getTable();
		ColumnCollection columns = table.getColumns();
		S builder = createSqlBuilder();
		addInsertIntoTable(table, row, builder);
		builder.lineBreak()._add("(");
		builder.appendIndent(1);
		boolean[] first=new boolean[]{false};
		for (int i = 0; i < columns.size(); i++) {
			Column column = columns.get(i);
			String def=this.getValueDefinitionForInsert(row, column);
			builder.$if(def!=null, ()->{
				if (!this.isFormulaColumn(column)) {
					builder.lineBreak();
					builder.comma(!first[0]).space(2, !first[0]);
					builder._add(def);
					first[0]=false;
				}
			});
		}
		builder.appendIndent(-1);
		builder.lineBreak()._add(")");
		addSql(sqlList, builder, SqlType.INSERT_ROW, row);
		return sqlList;
	}
}
