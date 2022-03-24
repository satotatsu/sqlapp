/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.mysql.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;

public class MySqlFunctionArgumentReader extends
		RoutineArgumentReader<Function> {

	protected MySqlFunctionArgumentReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<NamedArgument> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<NamedArgument> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				createNamedArguments(rs, productVersionInfo, result);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("functionArguments.sql");
	}

	protected void createNamedArguments(ExResultSet rs,
			ProductVersionInfo productVersionInfo, List<NamedArgument> result)
			throws SQLException {
		String paramList = getString(rs, "param_list");
		String[] args = paramList.split("\\s*,\\s*");
		for (String arg : args) {
			NamedArgument obj = createNamedArgument(rs, CommonUtils.trim(arg));
			result.add(obj);
		}
	}

	protected NamedArgument createNamedArgument(ExResultSet rs, String arg)
			throws SQLException {
		Function routine = new Function(getString(rs, ROUTINE_NAME));
		routine.setDialect(this.getDialect());
		routine.setCatalogName(getString(rs, CATALOG_NAME));
		routine.setSchemaName(getString(rs, SCHEMA_NAME));
		routine.setSpecificName(getString(rs, SPECIFIC_NAME));
		Matcher matcher = MySqlFunctionReader.ARGUMENT_PATTERN.matcher(arg);
		matcher.matches();
		String name = matcher.group(1);
		String productDataType = matcher.group(2);
		NamedArgument obj = createObject(name);
		obj.setCatalogName(getString(rs, CATALOG_NAME));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		SchemaUtils.setRoutine(obj, routine);
		this.getDialect().setDbType(productDataType, null,null,
				obj);
		return obj;
	}

}
