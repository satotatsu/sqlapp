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

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.UniqueConstraint;

/**
 * DB2のユニーク制約読み込みクラス
 * 
 * @author satoh
 * 
 */
public class Db2UniqueConstraintReader extends UniqueConstraintReader {

	public Db2UniqueConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<UniqueConstraint> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final java.util.Map<List<String>, UniqueConstraint> map = new java.util.LinkedHashMap<>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalogName = null;
				String schemaName = getString(rs, SCHEMA_NAME);
				String tableName = getString(rs, TABLE_NAME);
				String constraintName = getString(rs, CONSTRAINT_NAME);
				// String expression=getString(rs, "filter_condition");
				boolean primary = "P".equalsIgnoreCase(getString(rs, "TYPE"));
				UniqueConstraint c = map.get(List.of(schemaName, tableName, constraintName));
				if (c == null) {
					c = new UniqueConstraint(constraintName, primary);
					c.setCatalogName(catalogName);
					c.setSchemaName(schemaName);
					c.setTableName(tableName);
					c.setEnable("Y".equalsIgnoreCase(getString(rs, "ENFORCED")));
					c.setRemarks(getString(rs, REMARKS));
					Index index = c.getIndex();
					String indexName = getString(rs, "backing_index_name");
					if (indexName != null)
						index.setName(indexName);
					String indexSchema = getString(rs, "backing_index_schema");
					if (indexSchema != null)
						index.getSpecifics().put("INDEX_SCHEMA", indexSchema);
					index.setIndexType(Db2Utils.getIndexType(getString(rs, "INDEXTYPE")));
					index.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
					index.setRemarks(getString(rs, REMARKS));
					index.setCompression("Y".equalsIgnoreCase(getString(rs, "COMPRESSION")));
					setSpecifics(rs, "MINPCTUSED", index);
					setSpecifics(rs, "REVERSE_SCANS", index);
					map.put(List.of(schemaName, tableName, constraintName), c);
				}
				String colOrder = getString(rs, "COLORDER");
				String columnName = getString(rs, COLUMN_NAME);
				if ("I".equalsIgnoreCase(colOrder)) {
					c.getColumns().add(new Column(columnName), true);
				} else {
					c.getColumns().add(new Column(columnName), Order.parse(colOrder));
				}
			}
		});
		return new java.util.ArrayList<>(map.values());
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("uniqueConstraints.sql");
	}

}
