/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.db2.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.ProductVersionInfo;

/**
 * DB2 Function Argument Reader
 *  
 * @author satoh
 * 
 */
public class Db2FunctionArgumentReader extends RoutineArgumentReader<Function> {

	protected Db2FunctionArgumentReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<NamedArgument> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<NamedArgument> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				NamedArgument column = createArgument(rs);
				result.add(column);
			}
		});
		return result;
	}

	protected NamedArgument createArgument(ExResultSet rs) throws SQLException {
		NamedArgument obj = createObject("PARMNAME");
		Long length = this.getLong(rs, "LENGTH");
		Integer scale = this.getInteger(rs, "SCALE");
		obj.setCollation(getString(rs, "COLLATIONNAME"));
		obj.setDefaultValue(getString(rs, "DEFAULT"));
		obj.setRemarks(getString(rs, REMARKS));
		String rowType=getString(rs, "ROWTYPE");
		if ("B".equalsIgnoreCase(rowType)){
			obj.setDirection(ParameterDirection.Inout);
		}else if ("O".equalsIgnoreCase(rowType)){
			obj.setDirection(ParameterDirection.Output);
		}else if ("P".equalsIgnoreCase(rowType)){
			obj.setDirection(ParameterDirection.Input);
		}
		String productDataType = getString(rs, "TYPENAME");
		this.getDialect().setDbType(productDataType, length, scale, obj);
		return obj;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("functionArguments.sql");
	}

}
