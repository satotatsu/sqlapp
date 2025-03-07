/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.MviewLogColumnReader;
import com.sqlapp.data.db.metadata.MviewLogReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.MviewLog;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Oracleのマテビューログ読込
 * 
 * @author satoh
 * 
 */
public class OracleMviewLogReader extends MviewLogReader {

	protected OracleMviewLogReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<MviewLog> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<MviewLog> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				MviewLog mviewLog = createMviewLog(rs, this);
				result.add(mviewLog);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("mviewLogs.sql");
	}

	protected MviewLog createMviewLog(ExResultSet rs, ResultSetNextHandler handler) throws SQLException {
		MviewLog obj = new MviewLog(getString(rs, "LOG_TABLE"));
		obj.setSchemaName(getString(rs, "LOG_OWNER"));
		obj.setMasterTableName(getString(rs, "MASTER"));
		obj.setSaveRowIds("YES".equalsIgnoreCase(getString(rs, "ROWIDS")));
		obj.setSavePrimaryKey("YES".equalsIgnoreCase(getString(rs, "PRIMARY_KEY")));
		obj.setSaveObjectId("YES".equalsIgnoreCase(getString(rs, "OBJECT_ID")));
		obj.setSaveFilterColumns("YES".equalsIgnoreCase(getString(rs,
				"FILTER_COLUMNS")));
		obj.setSaveSequence("YES".equalsIgnoreCase(getString(rs, "SEQUENCE")));
		obj.setIncludeNewValues("YES".equalsIgnoreCase(getString(rs,
				"INCLUDE_NEW_VALUES")));
		obj.setIncludeNewValues("YES".equalsIgnoreCase(getString(rs,
				"INCLUDE_NEW_VALUES")));
		return obj;
	}

	@Override
	protected MviewLogColumnReader newMviewLogColumnReader() {
		return new OracleMviewLogColumnReader(this.getDialect());
	}
}
