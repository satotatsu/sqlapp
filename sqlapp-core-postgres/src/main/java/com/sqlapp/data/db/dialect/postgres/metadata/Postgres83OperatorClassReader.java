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
import com.sqlapp.data.db.metadata.FunctionFamilyReader;
import com.sqlapp.data.db.metadata.OperatorClassReader;
import com.sqlapp.data.db.metadata.OperatorFamilyReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.OperatorClass;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * PostgresのOperatorClass読み込みクラス
 * 
 * @author satoh
 * 
 */
public class Postgres83OperatorClassReader extends OperatorClassReader {

	protected Postgres83OperatorClassReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<OperatorClass> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<OperatorClass> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				OperatorClass obj = createOperatorClass(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("operatorClasses83.sql");
	}

	protected OperatorClass createOperatorClass(ExResultSet rs)
			throws SQLException {
		OperatorClass obj = new OperatorClass(getString(rs,
				"operator_class_name"));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		obj.setDialect(this.getDialect());
		obj.setIndexType(getString(rs, "index_type"));
		obj.setDefault(rs.getBoolean("operator_default"));
		obj.setDataTypeName(getString(rs, "data_type"));
		return obj;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.metadata.OperatorClassReader#
	 * newOperatorFamilyReader()
	 */
	@Override
	protected OperatorFamilyReader newOperatorFamilyReader() {
		return new Postgres83OperatorFamilyReader(this.getDialect());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.metadata.OperatorClassReader#
	 * newFunctionFamilyReader()
	 */
	@Override
	protected FunctionFamilyReader newFunctionFamilyReader() {
		return new Postgres83FunctionFamilyReader(this.getDialect());
	}
}
