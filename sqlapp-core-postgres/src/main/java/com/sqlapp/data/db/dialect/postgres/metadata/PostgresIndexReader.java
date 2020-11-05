/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.postgres.metadata;

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.map;
import static com.sqlapp.util.CommonUtils.min;
import static com.sqlapp.util.CommonUtils.trim;
import static com.sqlapp.util.CommonUtils.tripleKeyMap;
import static com.sqlapp.util.CommonUtils.unwrap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.TripleKeyMap;

/**
 * Postgresのインデックス読み込み
 * 
 * @author satoh
 * 
 */
public class PostgresIndexReader extends IndexReader {

	public PostgresIndexReader(Dialect dialect) {
		super(dialect);
	}

	/**
	 * インデックスのカラムを取得するための正規表現
	 */
	private static final Pattern indexPattern = Pattern.compile(
			"create\\s+.*index.*\\s+on\\s+.*\\((.*)\\).*",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE);

	/**
	 * インデックスのwhere条件を取得するための正規表現
	 */
	private static final Pattern indexWherePattern = Pattern.compile(
			"create\\s+.*index.*\\s+on\\s+.*\\((.*)\\).*\\s+where\\s+(.*)",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE);

	@Override
	protected List<Index> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Index> result = list();
		final TripleKeyMap<String, String, String, Index> map = tripleKeyMap();
		final Map<String, String> columnsMap = map();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String schema_name = getString(rs, SCHEMA_NAME);
				String index_name = getString(rs, INDEX_NAME);
				String table_name = getString(rs, TABLE_NAME);
				Index index = map.get(schema_name, table_name, index_name);
				if (index == null) {
					columnsMap.clear();
					index = new Index(index_name);
					index.setTableName(table_name);
					index.setSchemaName(schema_name);
					boolean isUnique = rs.getBoolean("is_unique");
					// boolean isPrimary=rs.getBoolean("is_primary");
					String type = getString(rs, "index_type");
					String remarks = getString(rs, "remarks");
					String indexprs = getString(rs, "indexprs");
					String definition = getString(rs, "definition");
					IndexType indexType = null;
					if (!isEmpty(indexprs)) {
						indexType = IndexType.Function;
					} else {
						indexType = IndexType.parse(type);
					}
					if (indexType != null) {
						index.setIndexType(indexType);
					}
					Matcher matcher = indexWherePattern.matcher(definition);
					if (matcher.matches()) {
						columnsMap.put(index.getName(), matcher.group(1));
						String condition = matcher.group(2);
						index.setWhere(unwrap(condition, '(', ')'));
					} else {
						matcher = indexPattern.matcher(definition);
						if (matcher.matches()) {
							columnsMap.put(index.getName(), matcher.group(1));
						}
					}
					index.setUnique(isUnique);
					index.setRemarks(remarks);
					map.put(schema_name, table_name, index_name, index);
					result.add(index);
				}
				String columnName = getString(rs, COLUMN_NAME);
				String columns = columnsMap.get(index.getName());
				int pos = min(
						columnName.length() + columns.indexOf(columnName),
						columns.length());
				String sub = trim(columns.substring(pos).toUpperCase());
				if (sub.startsWith("DESC")) {
					index.getColumns().add(columnName, Order.Desc);
				} else {
					index.getColumns().add(columnName, Order.Asc);
				}
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("indexes.sql");
	}

}
