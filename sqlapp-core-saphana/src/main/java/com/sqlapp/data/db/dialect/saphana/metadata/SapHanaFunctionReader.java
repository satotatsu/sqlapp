/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-saphana.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.saphana.metadata;

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

public class SapHanaFunctionReader extends FunctionReader {

	public SapHanaFunctionReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Function> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final Dialect dialact = this.getDialect();
		final List<Function> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Function function = createFunction(rs);
				function.setDialect(dialact);
				result.add(function);
			}
		});
		return result;
	}

	protected Function createFunction(ExResultSet rs) throws SQLException {
		String name = getString(rs, FUNCTION_NAME);
		Function function = new Function(name);
		function.setSchemaName(getString(rs, SCHEMA_NAME));
		function.setDefinition(getString(rs, "DEFINITION"));
		function.setId("" + rs.getLong("FUNCTION_OID"));
		return function;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("functions.sql");
		return node;
	}

	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return new SapHanaFunctionArgumentReader(this.getDialect());
	}
}
