/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static com.sqlapp.util.CommonUtils.doubleKeyMap;
import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CheckConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.DoubleKeyMap;

/**
 * Oracleのチェック制約作成クラス
 * 
 * @author satoh
 * 
 */
public class OracleCheckConstraintReader extends CheckConstraintReader {

	public OracleCheckConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<CheckConstraint> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<CheckConstraint> result = list();
		final DoubleKeyMap<String, String, CheckConstraint> map = doubleKeyMap();
		final DoubleKeyMap<String, String, List<Column>> colMap = doubleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String expression = getString(rs, "SEARCH_CONDITION");
				String schema_name = getString(rs, "OWNER");
				String table_name = getString(rs, TABLE_NAME);
				String constraint_name = getString(rs, "CONSTRAINT_NAME");
				CheckConstraint c = map.get(schema_name, constraint_name);
				List<Column> columnList = colMap.get(schema_name,
						constraint_name);
				if (c == null) {
					if (isNotNullConstraint(expression)) {
						return;
					}
					c = new CheckConstraint(constraint_name, expression);
					columnList = list();
					colMap.put(schema_name, constraint_name, columnList);
					c.setSchemaName(schema_name);
					c.setLastAlteredAt(rs.getTimestamp("LAST_CHANGE"));
					c.setTableName(table_name);
					String deferrable = getString(rs, "DEFERRABLE");
					String deferred = getString(rs, "DEFERRED");
					c.setDeferrability(OracleMetadataUtils.getDeferrability(
							deferrable, deferred));
					c.setEnable("INVALID".equalsIgnoreCase(getString(rs,
							"INVALID")));
					setSpecifics(rs, "GENERATED", c);
					result.add(c);
					map.put(schema_name, constraint_name, c);
				}
				Column column = new Column(getString(rs, COLUMN_NAME));
				column.setTableName(table_name);
				columnList.add(column);
			}
		});
		for (CheckConstraint c : result) {
			List<Column> columnList = colMap
					.get(c.getSchemaName(), c.getName());
			String generated = (String) c.getSpecifics().get("GENERATED");
			if ("GENERATED NAME".equalsIgnoreCase(generated)
					&& columnList.size() == 1) {
				c.setColumns(columnList.toArray(new Column[0]));
			}
		}
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("checkConstraints.sql");
	}

	private boolean isNotNullConstraint(String val) {
		Matcher matcher = NOT_NULL_PETTERN.matcher(val);
		return matcher.matches();
	}

	private static final Pattern NOT_NULL_PETTERN = Pattern.compile(
			"[\\S]+\\s+IS\\s+NOT\\s+NULL\\s*", Pattern.CASE_INSENSITIVE);
}
