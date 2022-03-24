/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.oracle.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.MviewLogColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Oracleのマテビューログカラム読込
 * 
 * @author satoh
 * 
 */
public class OracleMviewLogColumnReader extends MviewLogColumnReader {

	protected OracleMviewLogColumnReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ReferenceColumn> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final boolean dba = OracleMetadataUtils.hasSelectPrivilege(connection,
				this.getDialect(), "SYS", "DBA_MVIEW_LOG_FILTER_COLS");
		final List<ReferenceColumn> result = list();
		if (!dba) {
			return result;
		}
		SqlNode node = getSqlSqlNode(productVersionInfo);
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				ReferenceColumn column = createColumn(rs);
				result.add(column);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("mviewLogFilterColumns.sql");
	}

	protected ReferenceColumn createColumn(ExResultSet rs) throws SQLException {
		Column column = new Column(getString(rs, COLUMN_NAME));
		ReferenceColumn obj = new ReferenceColumn(column);
		obj.setSchemaName(getString(rs, "OWNER"));
		obj.setTableName(getString(rs, "NAME"));
		return obj;
	}
}
