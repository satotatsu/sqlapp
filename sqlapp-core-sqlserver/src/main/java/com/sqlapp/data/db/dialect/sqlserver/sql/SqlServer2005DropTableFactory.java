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

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

public class SqlServer2005DropTableFactory extends
		AbstractDropTableFactory<SqlServerSqlBuilder> {
	
	@Override
	public List<SqlOperation> createSql(Table obj) {
		SqlServerSqlBuilder builder = createSqlBuilder();
		addDropObject(obj, builder);
		List<SqlOperation> sqlList = list();
		addIfNotExists(obj, sqlList);
		addSql(sqlList, builder, SqlType.DROP, obj);
		return sqlList;
	}
	
	protected void addIfNotExists(Table obj, List<SqlOperation> sqlList) {
		if (this.getOptions().isDropIfExists()) {
			SqlServerSqlBuilder builder = this.newSqlBuilder(getDialect());
			builder.dropIfExists(obj);
			addSql(sqlList, builder, SqlType.DROP, obj);
		}
	}
	
}
