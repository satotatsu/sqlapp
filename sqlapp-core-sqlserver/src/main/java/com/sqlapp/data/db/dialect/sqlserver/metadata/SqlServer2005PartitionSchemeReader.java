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

import static com.sqlapp.util.CommonUtils.doubleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.PartitionSchemeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.PartitionScheme;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.TableSpace;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.DoubleKeyMap;

/**
 * パーティションスキーム読み込み
 * 
 * @author satoh
 * 
 */
public class SqlServer2005PartitionSchemeReader extends PartitionSchemeReader {

	protected SqlServer2005PartitionSchemeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<PartitionScheme> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final DoubleKeyMap<String, String, PartitionScheme> map = doubleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalog_name = getString(rs, CATALOG_NAME);
				String name = getString(rs, PARTITION_SHCEME_NAME);
				PartitionScheme obj = map.get(catalog_name, name);
				if (obj == null) {
					obj = createPartitionScheme(rs);
					map.put(catalog_name, name, obj);
				}
				obj.getTableSpaces().add(new TableSpace(getString(rs, "file_group_name")));
			}
		});
		return map.toList();
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("partitionSchemes2005.sql");
	}

	protected PartitionScheme createPartitionScheme(ExResultSet rs)
			throws SQLException {
		String catalog_name = getString(rs, CATALOG_NAME);
		String name = getString(rs, PARTITION_SHCEME_NAME);
		PartitionScheme obj = new PartitionScheme(name);
		obj.setCatalogName(catalog_name);
		obj.setDefault(rs.getBoolean("is_default"));
		obj.setId("" + rs.getInt("data_space_id"));
		obj.setPartitionFunctionName(getString(rs, "function_name"));
		return obj;
	}

}
