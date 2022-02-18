/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.sqlserver.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
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
 * SqlServer2005のプロシージャ読み込みクラス
 * 
 * @author satoh
 * 
 */
public class SqlServer2005ProcedureReader extends ProcedureReader {

	protected SqlServer2005ProcedureReader(Dialect dialect) {
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
				Procedure obj = createProcedure(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("procedures2005.sql");
	}

	protected Procedure createProcedure(ExResultSet rs) throws SQLException {
		Procedure obj = new Procedure(getString(rs, PROCEDURE_NAME));
		obj.setDialect(getDialect());
		obj.setCatalogName(getString(rs, CATALOG_NAME));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		obj.setCreatedAt(rs.getTimestamp("create_date"));
		obj.setLastAlteredAt(rs.getTimestamp("modify_date"));
		obj.setClassNamePrefix(getString(rs, "assembly_name"));
		obj.setClassName(getString(rs, "assembly_class"));
		obj.setMethodName(getString(rs, "assembly_method"));
		String definition = getString(rs, "definition");
		if (this.getReaderOptions().isReadDefinition()) {
			obj.setDefinition(definition);
		}
		if (this.getReaderOptions().isReadStatement()) {
			obj.setStatement(SqlServerUtils.getProcedureStatement(definition));
		}
		obj.setExecuteAs(getString(rs, "execute_as"));
		setSpecifics(rs, "assembly_id", obj);
		return obj;
	}

	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return new SqlServer2005ProcedureArgumentReader(this.getDialect());
	}
}
