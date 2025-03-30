/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.generator.factory;

import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.data.schemas.Table;

public class InsertSqlFactory {

	public List<SqlOperation> createSql(Table table, SqlType sqlType, Dialect dialect, TableOptions option) {
		final SqlFactoryRegistry sqlFactoryRegistry = dialect.createSqlFactoryRegistry();
		sqlFactoryRegistry.getOption().setTableOptions(option);
		final SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, sqlType);
		final List<SqlOperation> operations = factory.createSql(table);
		return operations;
	}

	public String toText(List<SqlOperation> operations) {
		StringBuilder builder = new StringBuilder();
		long cnt = operations.stream().filter(o -> !o.getSqlType().isComment() && !o.getSqlType().isEmptyLine())
				.count();
		if (cnt > 1) {

		} else {

		}
		return builder.toString();
	}

}
