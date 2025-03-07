/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ProcedureReader;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * SqlServer2000のプロシージャ作成クラス
 * 
 * @author satoh
 * 
 */
public class SqlServer2000ProcedureReader extends ProcedureReader {

	protected SqlServer2000ProcedureReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Procedure> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Procedure> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Procedure procedure = createProcedure(rs);
				result.add(procedure);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("procedures2000.sql");
		return node;
	}

	protected Procedure createProcedure(ExResultSet rs) throws SQLException {
		Timestamp created = rs.getTimestamp("created");
		Timestamp lastAltered = rs.getTimestamp("last_altered");
		Procedure obj = new Procedure(getString(rs, ROUTINE_NAME));
		obj.setSpecificName(getString(rs, SPECIFIC_NAME));
		obj.setCatalogName(getString(rs, "specific_catalog"));
		obj.setSchemaName(getString(rs, "specific_schema"));
		obj.setCreatedAt(created);
		obj.setLastAlteredAt(lastAltered);
		String difinition = getString(rs, "routine_definition");
		if (this.getReaderOptions().isReadDefinition()) {
			obj.setDefinition(difinition);
		}
		if (this.getReaderOptions().isReadStatement()) {
			obj.setStatement(SqlServerUtils.getViewStatement(difinition));
		}
		return obj;
	}

	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return null;
	}
}
