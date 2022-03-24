/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.postgres.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.OperatorReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Operator;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * PostgresのOperator作成クラス
 * 
 * @author satoh
 * 
 */
public class Postgres83OperatorReader extends OperatorReader {

	protected Postgres83OperatorReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Operator> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		if (!context.containsKey("id")) {
			context.put("id", (Object) null);
		}
		final List<Operator> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Operator operator = createOperator(rs);
				result.add(operator);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("operators83.sql");
	}

	protected Operator createOperator(ExResultSet rs) throws SQLException {
		Operator obj = new Operator(getString(rs, "oprname"));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		obj.setDialect(this.getDialect());
		obj.setId(getString(rs, "oid"));
		obj.setLeftArgument(getString(rs, "left_type"));
		obj.setRightArgument(getString(rs, "right_type"));
		obj.setFunctionSchemaName(getString(rs, "code_function_schema"));
		obj.setFunctionName(getString(rs, "code_function_name"));
		obj.setRestrictFunctionName(getString(rs, "rest_function_name"));
		if (obj.getRestrictFunction() != null) {
			obj.getRestrictFunction().setSchemaName(
					getString(rs, "rest_function_schema"));
		}
		obj.setJoinFunctionName(getString(rs, "join_function_name"));
		if (obj.getJoinFunction() != null) {
			obj.getJoinFunction().setSchemaName(getString(rs, "join_function_schema"));
		}
		obj.setCommutativeOperatorName(getString(rs, "oprcom_name"));
		obj.setNegationOperatorName(getString(rs, "oprnegate_name"));
		return obj;
	}
}
