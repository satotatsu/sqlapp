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

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * DELETE ROW生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractDeleteRowFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractRowFactory<S> {

	@Override
	protected List<SqlOperation> getOperations(final Row row) {
		final List<SqlOperation> sqlList = list();
		final Table table = row.getTable();
		final S builder = createSqlBuilder();
		addDeleteFromTable(table, row, builder);
		addSql(sqlList, builder, SqlType.DELETE_ROW, row);
		return sqlList;
	}

	protected void addDeleteFromTable(final Table table, final Row row,
			final S builder) {
		addDeleteFromTable(table, builder);
		builder.lineBreak().where()._true();
		addUniqueColumnsCondition(table, row, builder);
	}

	protected void addDeleteFromTable(final Table obj, final S builder) {
		builder.delete().from();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
		this.addTableComment(obj, builder);
	}

}
