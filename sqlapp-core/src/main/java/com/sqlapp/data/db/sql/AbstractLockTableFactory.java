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
 * LOCK TABLE
 * 
 * @author satoh
 * 
 */
public abstract class AbstractLockTableFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractTableFactory<S> {

	@Override
	public List<SqlOperation> createSql(final Table table) {
		final List<SqlOperation> sqlList = list();
		final S builder = createSqlBuilder();
		addLockTable(table, builder);
		addSql(sqlList, builder, SqlType.LOCK, table);
		return sqlList;
	}

	protected void addLockTable(final Table obj, final S builder) {
		builder.lock().table();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
		this.addTableComment(obj, builder);
		final TableLockMode tableLockMode=getLockMode(obj);
		addLockMode(obj, tableLockMode, builder);
	}

	protected void addLockMode(final Table obj, final TableLockMode tableLockMode, final S builder) {
		builder.$if(tableLockMode!=null, ()->{
			builder.in();
			builder.lockMode(tableLockMode).mode();
		});
	}

}
