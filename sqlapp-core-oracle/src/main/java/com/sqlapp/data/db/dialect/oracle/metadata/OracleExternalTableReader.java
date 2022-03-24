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

import static com.sqlapp.util.CommonUtils.doubleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ExternalTableReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ExternalTable;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.DoubleKeyMap;

/**
 * OracleのExternalTable読み込みクラス
 * 
 * @author satoh
 * 
 */
public class OracleExternalTableReader extends ExternalTableReader {

	protected OracleExternalTableReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ExternalTable> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final DoubleKeyMap<String, String, ExternalTable> map = doubleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String schema_name = getString(rs, "OWNER");
				String name = getString(rs, TABLE_NAME);
				ExternalTable obj = map.get(schema_name, name);
				if (obj == null) {
					obj = createExternalTable(rs);
					map.put(schema_name, name, obj);
				}
			}
		});
		return map.toList();
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("externalTables.sql");
	}

	protected ExternalTable createExternalTable(ExResultSet rs)
			throws SQLException {
		ExternalTable obj = new ExternalTable(getString(rs, TABLE_NAME));
		obj.setSchemaName(getString(rs, "OWNER"));
		obj.setTypeName(getString(rs, "TYPE_NAME"));
		obj.getType().setSchemaName(getString(rs, "TYPE_OWNER"));
		obj.setDefaultDirectoryName(getString(rs, "DEFAULT_DIRECTORY_NAME"));
		obj.setDirectoryName(getString(rs, "DIRECTORY_NAME"));
		obj.setRejectLimit(getString(rs, "REJECT_LIMIT"));
		obj.setAccessType(getString(rs, "ACCESS_TYPE"));
		obj.setAccessParameters(getString(rs, "ACCESS_PARAMETERS"));
		obj.setLocation(getString(rs, "LOCATION"));
		obj.setProperty(getString(rs, "PROPERTY"));
		obj.setRemarks(getString(rs, "COMMENTS"));
		return obj;
	}

}
