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
import static com.sqlapp.util.CommonUtils.notZero;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

public class MySqlProcedureArgument553Reader extends
		MySqlProcedureArgumentReader {

	protected MySqlProcedureArgument553Reader(Dialect dialect) {
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
				NamedArgument column = createNamedArgument(rs);
				result.add(column);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		if (productVersionInfo.gte(5, 5, 3)) {
			return getSqlNodeCache().getString("procedureArguments553.sql");
		} else {
			return super.getSqlSqlNode(productVersionInfo);
		}
	}

	protected void createNamedArguments(ExResultSet rs,
			ProductVersionInfo productVersionInfo, List<NamedArgument> result)
			throws SQLException {
		if (productVersionInfo.gte(5, 5, 3)) {
			NamedArgument obj = createNamedArgument(rs);
			result.add(obj);
		} else {
			super.createNamedArguments(rs, productVersionInfo, result);
		}
	}

	protected NamedArgument createNamedArgument(ExResultSet rs)
			throws SQLException {
		Procedure routine = new Procedure();
		routine.setCatalogName(getString(rs, SPECIFIC_CATALOG));
		routine.setSchemaName(getString(rs, SPECIFIC_SCHEMA));
		routine.setName(getString(rs, SPECIFIC_NAME));
		routine.setSpecificName(getString(rs, SPECIFIC_NAME));
		NamedArgument obj = new NamedArgument(getString(rs, PARAMETER_NAME));
		SchemaUtils.setRoutine(obj, routine);
		obj.setCatalogName(getString(rs, SPECIFIC_CATALOG));
		obj.setSchemaName(getString(rs, SPECIFIC_SCHEMA));
		String productDataType = getString(rs, "DATA_TYPE");
		long maxLength = rs.getLong("CHARACTER_MAXIMUM_LENGTH");
		long numericPrecision = rs.getLong("NUMERIC_PRECISION");
		Integer numericScale = getInteger(rs, "NUMERIC_SCALE");
		getDialect().setDbType(productDataType, notZero(maxLength, numericPrecision), numericScale, obj);
		obj.setCharacterSet(getString(rs, CHARACTER_SET_NAME));
		obj.setCollation(getString(rs, COLLATION_NAME));
		obj.setDirection(ParameterDirection.parse(getString(rs,
				"PARAMETER_MODE")));
		return obj;
	}

}
