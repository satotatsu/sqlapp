/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.firebird.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.trim;

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

public class FirebirdProcedureReader extends ProcedureReader {

	protected FirebirdProcedureReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Procedure> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("procedures.sql");
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

	protected Procedure createProcedure(ExResultSet rs) throws SQLException {
		String name = trim(getString(rs, "RDB$PROCEDURE_NAME"));
		String source = trim(getString(rs, "RDB$PROCEDURE_SOURCE"));
		Procedure procedure = new Procedure(name);
		if (this.getReaderOptions().isReadStatement()) {
			procedure.setStatement(source);
		}
		return procedure;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.metadata.RoutineReader#newRoutineArgumentReader()
	 */
	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return new FirebirdProcedureArgumentReader(this.getDialect());
	}
}
