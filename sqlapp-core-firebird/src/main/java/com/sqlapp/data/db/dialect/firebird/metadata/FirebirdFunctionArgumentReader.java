/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.firebird.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

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

public class FirebirdFunctionArgumentReader extends RoutineArgumentReader<Function> {

	protected FirebirdFunctionArgumentReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<NamedArgument> doGetAll(Connection connection, ParametersContext context,
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
		return getSqlNodeCache().getString("functionArguments.sql");
	}

	protected NamedArgument createNamedArgument(ExResultSet rs) throws SQLException {
		Function routine = new Function(getString(rs, ROUTINE_NAME));
		routine.setDialect(this.getDialect());
		NamedArgument obj = createObject();
		SchemaUtils.setRoutine(obj, routine);
		// int segmentLength = rs.getInt("SEGMENT_LENGTH");
		int segmentLength = rs.getInt("CHAR_LEN");
		int type = rs.getInt("FIELD_TYPE");
		int subType = rs.getInt("FIELD_SUB_TYPE");
		int length = getInt(rs, "FIELD_LENGTH");
		int precision = getInt(rs, "FIELD_PRECISION");
		Integer scale = getInteger(rs, "FIELD_SCALE");
		obj.setCharacterSet(getString(rs, CHARACTER_SET_NAME));
		obj.setCollation(getString(rs, COLLATION_NAME));
		FirebirdUtils.setDbType(obj, type, subType, length, precision, scale, segmentLength);
		return obj;
	}

}
