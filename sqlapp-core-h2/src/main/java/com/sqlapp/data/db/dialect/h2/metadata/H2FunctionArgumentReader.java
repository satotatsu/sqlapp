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

import com.sqlapp.data.db.datatype.DataType;
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

public class H2FunctionArgumentReader extends RoutineArgumentReader<Function> {

	protected H2FunctionArgumentReader(Dialect dialect) {
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
				NamedArgument obj = createNamedArgument(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("functionArguments.sql");
	}

	protected NamedArgument createNamedArgument(ExResultSet rs)
			throws SQLException {
		Function routine = new Function();
		routine.setDialect(this.getDialect());
		NamedArgument obj = createObject(COLUMN_NAME);
		routine.setCatalogName(getString(rs, "ALIAS_CATALOG"));
		routine.setSchemaName(getString(rs, "ALIAS_SCHEMA"));
		routine.setName(getString(rs, "ALIAS_NAME"));
		routine.setSpecificName(getString(rs, "ALIAS_NAME"));
		obj.setCatalogName(getString(rs, "ALIAS_CATALOG"));
		obj.setSchemaName(getString(rs, "ALIAS_SCHEMA"));
		obj.setSchemaName(getString(rs, "ALIAS_SCHEMA"));
		boolean nullable=this.toBoolean(getString(rs, "NULLABLE"));
		SchemaUtils.setRoutine(obj, routine);
		Long precision = getLong(rs, "PRECISION");
		Integer scale = getInteger(rs, "SCALE");
		DataType type=DataType.valueOf(rs.getInt("DATA_TYPE"));
		obj.setNotNull(!nullable);
		obj.setDataType(type);
		obj.setDataTypeName(type.toString());
		obj.setLength(precision);
		obj.setScale(scale);
		return obj;
	}

}
