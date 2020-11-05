/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.sqlserver.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractInsertRowFactory;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.Table;

public class SqlServerInsertRowFactory extends
		AbstractInsertRowFactory<SqlServerSqlBuilder> {

	@Override
	protected List<SqlOperation> getStartSqlOperations(DbCommonObject<?> obj) {
		Table table = toTable(obj);
		SqlFactory<Table> operation = this.getSqlFactoryRegistry()
				.getSqlFactory(table, SqlType.IDENTITY_ON);
		return operation.createSql(table);
	}

	@Override
	protected List<SqlOperation> getEndSqlOperations(DbCommonObject<?> obj) {
		Table table = toTable(obj);
		SqlFactory<Table> operation = this.getSqlFactoryRegistry()
				.getSqlFactory(table, SqlType.IDENTITY_OFF);
		return operation.createSql(table);
	}
}
