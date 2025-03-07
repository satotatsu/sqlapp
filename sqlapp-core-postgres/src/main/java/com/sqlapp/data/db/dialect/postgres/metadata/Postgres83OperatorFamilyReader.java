/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.OperatorFamilyReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.OperatorFamily;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * PostgresのOperatorFamily読み込みクラス
 * 
 * @author satoh
 * 
 */
public class Postgres83OperatorFamilyReader extends OperatorFamilyReader {

	protected Postgres83OperatorFamilyReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<OperatorFamily> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<OperatorFamily> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				OperatorFamily obj = createOperatorFamily(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("operatorFamilies83.sql");
	}

	protected OperatorFamily createOperatorFamily(ExResultSet rs)
			throws SQLException {
		OperatorFamily obj = new OperatorFamily();
		obj.setDialect(this.getDialect());
		obj.setStrategyNumber(rs.getInt("operator_family_strategy"));
		obj.setOperatorClassName(getString(rs, "operator_class_name"));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		obj.setOperatorName(getString(rs, OPERATOR_NAME));
		obj.getOperator().setSchemaName(getString(rs, "operator_schema"));
		obj.getOperator().setLeftArgument(getString(rs, "left_type"));
		obj.getOperator().setRightArgument(getString(rs, "right_type"));
		return obj;
	}

}
