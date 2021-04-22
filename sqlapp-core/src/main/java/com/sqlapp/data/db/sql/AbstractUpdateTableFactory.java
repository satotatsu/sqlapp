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
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * UPDATE TABLE生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractUpdateTableFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractTableFactory<S> {

	@Override
	public List<SqlOperation> createSql(final Table table) {
		final List<SqlOperation> sqlList = list();
		final S builder = createSqlBuilder();
		addUpdateTable(table, builder);
		addSql(sqlList, builder, getSqlType(), table);
		return sqlList;
	}
	
	protected abstract SqlType getSqlType();
	
	protected void addUpdateTable(final Table obj, final S builder) {
		builder.update();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
		builder.lineBreak().set();
		final List<Column> uniqueColumns = obj.getUniqueColumns();
		final ColumnCollection columns = obj.getColumns();
		final boolean[] first=new boolean[]{true};
		for (final Column column:columns) {
			if (uniqueColumns!=null&&uniqueColumns.contains(column)) {
				continue;
			}
			if (!isUpdateable(column)) {
				continue;
			}
			final String def=this.getValueDefinitionForUpdate(column);
			builder.$if(def!=null, ()->{
				builder.lineBreak();
				builder.comma(!first[0]);
				builder.name(column).space().eq();
				builder.space()._add(def);
				final String comment=this.getOptions().getTableOptions().getUpdateColumnComment().apply(column);
				if (!CommonUtils.isEmpty(comment)&&!CommonUtils.eqIgnoreCase(comment, column.getName())) {
					builder.space().addComment(comment);
				}
				first[0]=false;
			});
		}
		builder.lineBreak();
		builder.where()._true();
		addUpdateConditionColumns(obj, builder);
	}

	protected abstract void addUpdateConditionColumns(final Table table,
			S builder);
	
}
