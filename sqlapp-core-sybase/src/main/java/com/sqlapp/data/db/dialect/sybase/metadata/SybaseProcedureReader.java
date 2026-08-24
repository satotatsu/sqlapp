/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sybase.
 *
 * sqlapp-core-sybase is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sybase is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sybase.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sybase.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * SqlServerのプロシージャ作成クラス
 * 
 * @author satoh
 * 
 */
public class SybaseProcedureReader extends ProcedureReader {

	protected SybaseProcedureReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Procedure> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Procedure> result = list();
		final Map<Procedure, StringBuilder> definitions = new LinkedHashMap<>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String name = getString(rs, ROUTINE_NAME);
				Procedure procedure = result.isEmpty() ? null : result.get(result.size() - 1);
				if (procedure == null || !name.equals(procedure.getName())) {
					procedure = createProcedure(rs);
					result.add(procedure);
					definitions.put(procedure, new StringBuilder());
				}
				definitions.get(procedure).append(getString(rs, "routine_definition"));
			}
		});
		definitions.forEach((procedure, definition) -> setDefinition(procedure, definition.toString()));
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("procedures.sql");
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
		if (this.getReaderOptions().isReadDefinition()
				|| this.getReaderOptions().isReadStatement()) {
			obj.setDefinition(difinition);
		}
		return obj;
	}

	private void setDefinition(Procedure obj, String definition) {
		if (this.getReaderOptions().isReadDefinition()
				|| this.getReaderOptions().isReadStatement()) {
			obj.setDefinition(definition);
		}
	}

	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return null;
	}
}
