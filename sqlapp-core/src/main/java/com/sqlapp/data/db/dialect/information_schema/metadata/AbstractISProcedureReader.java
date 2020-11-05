/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.information_schema.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ProcedureReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.ProductVersionInfo;

/**
 * INFORMATION_SCHEMAのプロシージャ読み込みクラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractISProcedureReader extends ProcedureReader {

	protected AbstractISProcedureReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Procedure> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<Procedure> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Procedure routine = createProcedure(rs);
				result.add(routine);
			}
		});
		return result;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache(AbstractISProcedureReader.class).getString(
				"procedures.sql");
		return node;
	}

	protected Procedure createProcedure(ExResultSet rs) throws SQLException {
		Timestamp created = rs.getTimestamp("CREATED");
		Timestamp lastAltered = rs.getTimestamp("LAST_ALTERED");
		Procedure routine = new Procedure(getString(rs, "ROUTINE_NAME"));
		routine.setSpecificName(getString(rs, "SPECIFIC_NAME"));
		routine.setCatalogName(getString(rs, "ROUTINE_CATALOG"));
		routine.setSchemaName(getString(rs, "ROUTINE_SCHEMA"));
		routine.setCreatedAt(created);
		routine.setLastAlteredAt(lastAltered);
		routine.setDefinition(getString(rs, "ROUTINE_DEFINITION"));
		return routine;
	}
}
