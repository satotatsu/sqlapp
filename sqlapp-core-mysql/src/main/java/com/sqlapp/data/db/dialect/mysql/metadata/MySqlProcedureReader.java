/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ProcedureReader;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * MySqlのProcedure作成クラス
 * 
 * @author satoh
 * 
 */
public class MySqlProcedureReader extends ProcedureReader {

	protected MySqlProcedureReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Procedure> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Procedure> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Procedure obj = createProcedure(rs, productVersionInfo);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("procedures.sql");
	}

	protected Procedure createProcedure(ExResultSet rs,
			ProductVersionInfo productVersionInfo) throws SQLException {
		Procedure obj = new Procedure(getString(rs, ROUTINE_NAME));
		obj.setSpecificName(getString(rs, SPECIFIC_NAME));
		obj.setCatalogName(getString(rs, CATALOG_NAME));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		obj.setSqlDataAccess(getString(rs, SQL_DATA_ACCESS));
		obj.setSqlSecurity(getString(rs, SECURITY_TYPE));
		obj.setCreatedAt(rs.getTimestamp("created"));
		obj.setLastAlteredAt(rs.getTimestamp("modified"));
		try {
			obj.setStatement(MySqlUtils.readBlobAsString(rs, "body_utf8"));
		} catch (SQLException e) {
			obj.setStatement(MySqlUtils.readBlobAsString(rs, "body"));
		}
		String paramList = getString(rs, "param_list");
		String[] args = paramList.split("\\s*,\\s*");
		for (String arg : args) {
			NamedArgument argument = createObject();
			MySqlUtils.setProcedureNamedArgument(arg,
					argument);
			obj.getArguments().add(argument);
		}
		return obj;
	}

	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return null;
	}
	
	protected NamedArgument createObject(){
		NamedArgument obj=new NamedArgument();
		obj.setDialect(this.getDialect());
		return obj;
	}

	protected NamedArgument createObject(String name){
		NamedArgument obj=new NamedArgument(name);
		obj.setDialect(this.getDialect());
		return obj;
	}
}
