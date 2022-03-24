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

import static com.sqlapp.util.CommonUtils.abs;
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

public class FirebirdProcedureArgumentReader extends RoutineArgumentReader<Procedure> {

	protected FirebirdProcedureArgumentReader(Dialect dialect) {
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
		return getSqlNodeCache().getString("procedureArguments.sql");
	}

	protected NamedArgument createNamedArgument(ExResultSet rs) throws SQLException {
		Procedure routine = new Procedure(getString(rs, ROUTINE_NAME));
		routine.setDialect(this.getDialect());
		NamedArgument obj = createObject(getString(rs, PARAMETER_NAME));
		SchemaUtils.setRoutine(obj, routine);
		// int segmentLength = rs.getInt("SEGMENT_LENGTH");
		int segmentLength = rs.getInt("SEGMENT_LENGTH");
		int length = rs.getInt("FIELD_LENGTH");
		int precision = rs.getInt("FIELD_PRECISION");
		int scale = abs(rs.getInt("FIELD_SCALE"));
		short nullFlag = rs.getShort("NULL_FLAG");
		int type = rs.getInt("FIELD_TYPE");
		int subType = rs.getInt("FIELD_SUB_TYPE");
		if (nullFlag == 1) {
			obj.setNullable(true);
		} else {
			obj.setNullable(false);
		}
		obj.setCharacterSet(getString(rs, CHARACTER_SET_NAME));
		obj.setCollation(getString(rs, COLLATION_NAME));
		obj.setRemarks(getString(rs, REMARKS));
		int lowerBound = rs.getInt("LOWER_BOUND");
		int upperBound = rs.getInt("UPPER_BOUND");
		if (upperBound > 0) {
			obj.setArrayDimension(1);
			obj.setArrayDimensionLowerBound(lowerBound);
			obj.setArrayDimensionUpperBound(upperBound);
		}
		FirebirdUtils.setDbType(obj, type, subType, length, precision, scale, segmentLength);
		int direction = rs.getInt("PARAMETER_TYPE");
		if (direction == 0) {
			obj.setDirection(ParameterDirection.Input);
		} else if (direction == 1) {
			obj.setDirection(ParameterDirection.Output);
		} else if (direction == 2) {
			obj.setDirection(ParameterDirection.Inout);
		}
		return obj;
	}

}
