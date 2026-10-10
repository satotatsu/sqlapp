/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
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
import com.sqlapp.data.schemas.NullsOrder;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.TripleKeyMap;
import com.sqlapp.data.db.dialect.postgres.sql.PostgresCreateIndexFactory;

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
	private static final Pattern indexPattern = Pattern.compile("create\\s+.*index.*\\s+on\\s+.*\\((.*)\\).*",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE);

	/**
	 * インデックスのwhere条件を取得するための正規表現
	 */
	private static final Pattern indexWherePattern = Pattern.compile(
			"create\\s+.*index.*\\s+on\\s+.*\\((.*)\\).*\\s+where\\s+(.*)",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE);

	@Override
	protected List<Index> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final boolean catalogDetails = productVersionInfo != null && productVersionInfo.getMajorVersion() != null
				&& productVersionInfo.getMajorVersion() >= 11;
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
					readDefinition(index, definition);
					IndexType indexType = null;
					if (!isEmpty(indexprs)) {
						indexType = IndexType.Function;
					} else {
						indexType = IndexType.parse(type);
					}
					if (indexType != null) {
						index.setIndexType(indexType);
					}
					if (catalogDetails) {
						index.setWhere(getString(rs, "predicate"));
					} else {
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
					}
					index.setUnique(isUnique);
					if (rs.getBoolean("indnullsnotdistinct")) {
						index.getSpecifics().put(PostgresCreateIndexFactory.NULLS_NOT_DISTINCT, "true");
					}
					index.setRemarks(remarks);
					index.setTableSpaceName(getString(rs, "index_tablespace"));
					index.getSpecifics().put(PostgresCreateIndexFactory.METHOD, type);
					java.sql.Array options = rs.getArray("index_options");
					if (options != null) {
						try {
							var optionSql = new java.util.ArrayList<String>();
							for (Object option : (Object[]) options.getArray()) {
								String text = option.toString();
								int equal = text.indexOf('=');
								if (equal < 1) throw new SQLException("Malformed PostgreSQL index option: " + text);
								index.getSpecifics().put(text.substring(0, equal), text.substring(equal + 1));
								optionSql.add(getDialect().quote(text.substring(0, equal)) + "='"
										+ text.substring(equal + 1).replace("'", "''") + "'");
							}
						index.getSpecifics().put(PostgresCreateIndexFactory.OPTIONS, String.join(", ", optionSql));
						} finally { options.free(); }
					}
					map.put(schema_name, table_name, index_name, index);
					result.add(index);
				}
				String columnName = getString(rs, COLUMN_NAME);
				if (rs.getInt("num") > rs.getInt("indnkeyatts")) {
					index.getIncludes().add(columnName);
					return;
				}
				String keySql = getString(rs, "key_sql");
				String previousKeys = index.getSpecifics().get(PostgresCreateIndexFactory.KEYS);
				index.getSpecifics().put(PostgresCreateIndexFactory.KEYS,
						previousKeys == null ? keySql : previousKeys + ", " + keySql);
				if (catalogDetails) {
					index.getColumns().add(columnName, rs.getBoolean("is_desc") ? Order.Desc : Order.Asc);
					boolean nullsFirst = rs.getBoolean("nulls_first");
					if (!rs.wasNull()) {
						index.getColumns().get(index.getColumns().size() - 1)
								.setNullsOrder(nullsFirst ? NullsOrder.NullsFirst : NullsOrder.NullsLast);
					}
					return;
				}
				String columns = columnsMap.get(index.getName());
				int pos = min(columnName.length() + columns.indexOf(columnName), columns.length());
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

	/** Optional vendor hook using the definition already returned by the bulk query. */
	protected void readDefinition(Index index, String definition) { }

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		if (productVersionInfo != null && productVersionInfo.getMajorVersion() != null
				&& productVersionInfo.getMajorVersion() >= 15) {
			return getSqlNodeCache().getString("indexes150.sql");
		}
		if (productVersionInfo != null && productVersionInfo.getMajorVersion() != null
				&& productVersionInfo.getMajorVersion() >= 11) {
			return getSqlNodeCache().getString("indexes110.sql");
		}
		if (productVersionInfo != null && productVersionInfo.getMajorVersion() != null
				&& (productVersionInfo.getMajorVersion() > 9 || productVersionInfo.getMajorVersion() == 9
					&& productVersionInfo.getMinorVersion() != null && productVersionInfo.getMinorVersion() >= 1))
			return getSqlNodeCache().getString("indexes91.sql");
		return getSqlNodeCache().getString("indexes.sql");
	}

}
