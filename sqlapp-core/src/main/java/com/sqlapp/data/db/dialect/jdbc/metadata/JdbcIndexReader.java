/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.jdbc.metadata;

import static com.sqlapp.util.CommonUtils.emptyToNull;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.tripleKeyMap;
import static com.sqlapp.util.DbUtils.close;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.TripleKeyMap;

/**
 * 汎用のインデックス読み込みクラス
 * 
 * @author satoh
 * 
 */
public class JdbcIndexReader extends IndexReader {

	public JdbcIndexReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Index> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		List<Index> result = list();
		try {
			DatabaseMetaData databaseMetaData = connection.getMetaData();
			result.addAll(getAllIndex(databaseMetaData, context, false));
			return result;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	protected List<Index> getAllIndex(DatabaseMetaData databaseMetaData,
			ParametersContext context, boolean unique) throws SQLException {
		ResultSet rs = null;
		List<Index> result = list();
		try {
			rs = databaseMetaData.getIndexInfo(
					CommonUtils.coalesce(emptyToNull(this.getCatalogName(context)), emptyToNull(this.getCatalogName()))
					,CommonUtils.coalesce(emptyToNull(this.getSchemaName(context)), emptyToNull(this.getSchemaName()))
					, emptyToNull(this.getTableName(context))
					, unique, false);
			TripleKeyMap<String, String, String, Index> tMap = tripleKeyMap();
			while (rs.next()) {
				String table_catalog = getString(rs, "TABLE_CAT");
				String table_schema = getString(rs, "TABLE_SCHEM");
				String table_name = getString(rs, TABLE_NAME);
				String index_name = getString(rs, "INDEX_NAME");
				String column_name = getString(rs, COLUMN_NAME);
				Index index = tMap.get(table_catalog, table_schema, index_name);
				String ascOrdesc = getString(rs, "ASC_OR_DESC");
				Order order = Order.parse(ascOrdesc);
				if (index == null) {
					index = new Index(index_name);
					index.setCatalogName(table_catalog);
					index.setSchemaName(table_schema);
					index.setTableName(table_name);
					boolean nonUnique = rs.getBoolean("NON_UNIQUE");
					short type = rs.getShort("TYPE");
					if (type == DatabaseMetaData.tableIndexHashed) {
						index.setIndexType(IndexType.BTree);
					} else if (type == DatabaseMetaData.tableIndexClustered) {
						index.setIndexType(IndexType.Clustered);
					} else if (type == DatabaseMetaData.tableIndexOther) {
						index.setIndexType(IndexType.Other);
					} else if (type == DatabaseMetaData.tableIndexStatistic) {
					} else {
					}
					index.setUnique(!nonUnique);
					index.setWhere(getString(rs, "FILTER_CONDITION"));
					tMap.put(table_catalog, table_schema, index_name, index);
					result.add(index);
				}
				index.getColumns().add(new Column(column_name), order);
			}
			return result;
		} finally {
			close(rs);
		}
	}
}
