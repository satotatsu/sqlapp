/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-h2.
 *
 * sqlapp-core-h2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-h2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-h2.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.h2.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.FunctionReader;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * H2の関数読み込みクラス
 * 
 * @author satoh
 * 
 */
public class H2FunctionReader extends FunctionReader {

	protected H2FunctionReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Function> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("functionAliases.sql");
		final List<Function> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String alias_Catalog = getString(rs, "ALIAS_CATALOG");
				String alias_Schema = getString(rs, "ALIAS_SCHEMA");
				String alias_Name = getString(rs, "ALIAS_NAME");
				String java_class = getString(rs, "JAVA_CLASS");
				String java_method = getString(rs, "JAVA_METHOD");
				String remarks = getString(rs, REMARKS);
				Function obj = new Function(alias_Name);
				obj.setCatalogName(alias_Catalog);
				obj.setSchemaName(alias_Schema);
				obj.setClassName(java_class);
				obj.setMethodName(java_method);
				obj.setRemarks(remarks);
				result.add(obj);
			}
		});
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.metadata.RoutineReader#newRoutineArgumentReader()
	 */
	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return new H2FunctionArgumentReader(this.getDialect());
	}
}
