/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-db2.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.db2.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.tripleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.TripleKeyMap;
/**
 * DB2のインデックス読み込みクラス
 * 
 * @author satoh
 * 
 */
public class Db2IndexReader extends IndexReader {

	public Db2IndexReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Index> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<Index> result = list();
		final TripleKeyMap<String, String, String, Index> map = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalogName = null;
				String schemaName = getString(rs, SCHEMA_NAME);
				String name = getString(rs, INDEX_NAME);
				boolean uniqueness = !"D".equalsIgnoreCase(getString(rs,
						"UNIQUERULE"));
				Index index = map.get(catalogName, schemaName, name);
				if (index == null) {
					index = new Index(name);
					index.setCatalogName(catalogName);
					index.setSchemaName(schemaName);
					index.setTableName(getString(rs, TABLE_NAME));
					index.setUnique(uniqueness);
					index.setCompression("Y".equalsIgnoreCase(getString(rs,
							"COMPRESSION")));
					index.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
					index.setIndexType(Db2Utils.getIndexType(getString(rs,
							"INDEXTYPE")));
					index.setTableSpaceName(this.getString(rs, "table_space"));
					index.setRemarks(getString(rs, REMARKS));
					setSpecifics(rs, "PCTFREE", index);
					//
					setSpecifics(rs, "MINPCTUSED", index);
					setSpecifics(rs, "REVERSE_SCANS", index);
					//
					map.put(catalogName, schemaName, name, index);
					result.add(index);
				}
				String colOrder = getString(rs, "COLORDER");
				String columnName = getString(rs, COLUMN_NAME);
				if ("I".equalsIgnoreCase(colOrder)) {
					index.getColumns().add(new Column(columnName), true);
				} else {
					index.getColumns().add(new Column(columnName),
							Order.parse(colOrder));
				}
			}
		});
		return result;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("indexes.sql");
	}

}
