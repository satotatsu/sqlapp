/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ProcedureReader;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.ProductVersionInfo;

/**
 * DB2のプロシージャ読み込みクラス
 * 
 * @author satoh
 * 
 */
public class Db2ProcedureReader extends ProcedureReader {

	protected Db2ProcedureReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Procedure> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
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

	protected Procedure createProcedure(ExResultSet rs) throws SQLException {
		Procedure obj = new Procedure(getString(rs, ROUTINE_NAME));
		Db2Utils.setRutine(this.getDialect(), rs, this.getReaderOptions(), obj);
		obj.setParallel("Y".equalsIgnoreCase(this.getString(rs, "PARALLEL")));
		setSpecifics(rs, "DIALECT", obj);
		return obj;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("procedures.sql");
		return node;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.metadata.RoutineReader#newRoutineArgumentReader()
	 */
	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return new Db2ProcedureArgumentReader(this.getDialect());
	}

}
