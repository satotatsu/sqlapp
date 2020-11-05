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
import com.sqlapp.data.db.metadata.PartitionFunctionReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.PartitionFunction;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;

/**
 * パーティション関数読み込み
 * 
 * @author satoh
 * 
 */
public class SqlServer2005PartitionFunctionReader extends
		PartitionFunctionReader {

	protected SqlServer2005PartitionFunctionReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<PartitionFunction> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final DoubleKeyMap<String, String, PartitionFunction> map = doubleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalog_name = getString(rs, CATALOG_NAME);
				String name = getString(rs, PARTITION_FUNCTION_NAME);
				PartitionFunction obj = map.get(catalog_name, name);
				if (obj == null) {
					obj = createPartitionFunction(rs);
					map.put(catalog_name, name, obj);
				}
				obj.getValues().add(getString(rs, "value"));
			}
		});
		return map.toList();
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("partitionFunctions2005.sql");
	}

	protected PartitionFunction createPartitionFunction(ExResultSet rs)
			throws SQLException {
		String catalog_name = getString(rs, CATALOG_NAME);
		String name = getString(rs, PARTITION_FUNCTION_NAME);
		PartitionFunction obj = new PartitionFunction(name);
		obj.setCatalogName(catalog_name);
		obj.setCreatedAt(rs.getTimestamp("create_date"));
		obj.setLastAlteredAt(rs.getTimestamp("modify_date"));
		String productDataType = getString(rs, "type_name");
		Long byteLength = getLong(rs, "max_length");
		Long maxLength = SqlServerUtils.getMaxLength(productDataType,
				byteLength);
		Long precision=rs.getLongValue("precision");
		Integer scale=rs.getInteger("scale");
		obj.setDataTypeName(productDataType);
		this.getDialect().setDbType(productDataType, CommonUtils.notZero(maxLength, precision), scale, obj);
		obj.setBoundaryValueOnRight(rs.getBoolean("boundary_value_on_right"));
		obj.setId("" + rs.getInt("function_id"));
		return obj;
	}
}
