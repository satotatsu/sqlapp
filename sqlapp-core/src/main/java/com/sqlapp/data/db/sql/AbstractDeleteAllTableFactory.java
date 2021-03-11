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

import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * DELETE ALL生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractDeleteAllTableFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractTableFactory<S> {

	@Override
	public List<SqlOperation> createSql(final Table obj) {
		final List<SqlOperation> sqlList = list();
		final S builder = createSqlBuilder();
		addDeleteFromTable(obj, builder);
		if (this.getOptions().getTableOptions().getDeleteAllCondition()!=null){
			builder.lineBreak();
			builder.where()._true();
			this.getOptions().getTableOptions().getDeleteAllCondition().accept(obj, builder);
		}
		addSql(sqlList, builder, SqlType.DELETE_ALL, obj);
		return sqlList;
	}

	protected void addDeleteFromTable(final Table obj, final S builder) {
		builder.delete().from().table();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
	}

}
