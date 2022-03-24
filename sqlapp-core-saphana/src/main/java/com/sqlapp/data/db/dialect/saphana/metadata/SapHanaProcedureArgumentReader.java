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
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

public class SapHanaProcedureArgumentReader extends
		RoutineArgumentReader<Procedure> {

	protected SapHanaProcedureArgumentReader(Dialect dialect) {
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
		return getSqlNodeCache().getString("procedureArguments.sql");
	}

	protected NamedArgument createNamedArgument(ExResultSet rs)
			throws SQLException {
		Procedure routine = new Procedure(getString(rs, PROCEDURE_NAME));
		routine.setDialect(this.getDialect());
		routine.setSchemaName(getString(rs, SCHEMA_NAME));
		NamedArgument obj = createObject(getString(rs, PARAMETER_NAME));
		SchemaUtils.setRoutine(obj, routine);
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		String productDataType = getString(rs, "DATA_TYPE_NAME");
		long maxLength = rs.getLong("LENGTH");
		Integer numericScale = getInteger(rs, "SCALE");
		boolean nullable = toBoolean(getString(rs, "IS_NULLABLE"))
				.booleanValue();
		obj.setNullable(nullable);
		getDialect().setDbType(productDataType, maxLength, numericScale, obj);
		obj.setCharacterSet(getString(rs, "CHARACTER_SET_NAME"));
		obj.setCollation(getString(rs, COLLATION_NAME));
		obj.setDirection(ParameterDirection.parse(getString(rs,
				"PARAMETER_TYPE")));
		return obj;
	}

}
