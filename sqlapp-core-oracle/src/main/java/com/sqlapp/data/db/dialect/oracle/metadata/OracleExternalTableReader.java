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
	protected List<ExternalTable> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		boolean partitionLocations = supportsPartitionLocations(productVersionInfo);
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
				String location = getString(rs, "LOCATION");
				if (location != null) {
					int count = Integer.parseInt(obj.getSpecifics().getOrDefault("oracle.external.locationCount", "0"));
					String prefix = "oracle.external.location." + count + ".";
					obj.getSpecifics().put(prefix + "file", location);
					String directory = getString(rs, "DIRECTORY_NAME");
					if (directory != null)
						obj.getSpecifics().put(prefix + "directory", directory);
					String owner = getString(rs, "DIRECTORY_OWNER");
					if (owner != null)
						obj.getSpecifics().put(prefix + "directoryOwner", owner);
					if (partitionLocations) {
						String partition = getString(rs, "PARTITION_NAME");
						String subpartition = getString(rs, "SUBPARTITION_NAME");
						if (partition != null)
							obj.getSpecifics().put(prefix + "partition", partition);
						if (subpartition != null)
							obj.getSpecifics().put(prefix + "subpartition", subpartition);
					}
					obj.getSpecifics().put("oracle.external.locationCount", Integer.toString(count + 1));
				}
			}
		});
		return map.toList();
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		if (supportsPartitionLocations(productVersionInfo))
			return getSqlNodeCache().getString("externalTables12cR2.sql");
		return getSqlNodeCache().getString("externalTables.sql");
	}

	private boolean supportsPartitionLocations(ProductVersionInfo version) {
		if (version != null && version.getMajorVersion() != null) {
			int major = version.getMajorVersion();
			return major > 12 || major == 12 && version.getMinorVersion() != null && version.getMinorVersion() >= 2;
		}
		// The 12c dialect also represents 12.1. Only a later dialect is sufficient
		// evidence when the exact server version is unavailable.
		return getDialect() instanceof com.sqlapp.data.db.dialect.oracle.Oracle18c;
	}

	protected ExternalTable createExternalTable(ExResultSet rs) throws SQLException {
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
