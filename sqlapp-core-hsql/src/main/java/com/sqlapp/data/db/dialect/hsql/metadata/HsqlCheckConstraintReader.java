/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.hsql.metadata;

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.trim;
import static com.sqlapp.util.CommonUtils.tripleKeyMap;

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
import com.sqlapp.util.TripleKeyMap;

/**
 * HSQLのチェック制約作成クラス
 * 
 * @author satoh
 * 
 */
public class HsqlCheckConstraintReader extends CheckConstraintReader {

	public HsqlCheckConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<CheckConstraint> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		String tableName = this.getTableName(context);
		if (isEmpty(tableName)) {
			context.put("checkClause", (String) null);
		} else {
			context.put("checkClause", "%" + tableName + ".%");
		}
		final List<CheckConstraint> result = list();
		final TripleKeyMap<String, String, String, List<Column>> colMap = tripleKeyMap();
		final TripleKeyMap<String, String, String, CheckConstraint> tMap = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalogName = getString(rs, "CONSTRAINT_CATALOG");
				String schemaName = getString(rs, "CONSTRAINT_SCHEMA");
				String name = getString(rs, CONSTRAINT_NAME);
				String source = trim(getString(rs, "CHECK_CLAUSE"));
				String tableName = getString(rs, TABLE_NAME);
				String columnName = getString(rs, COLUMN_NAME);
				if (schemaName==null&&tableName==null){
					return;
				}
				if (isNotNullConstraint(schemaName, tableName, columnName,
						source)) {
					return;
				}
				CheckConstraint c = tMap.get(catalogName, schemaName, name);
				List<Column> cols = colMap.get(catalogName, schemaName, name);
				if (c == null) {
					c = new CheckConstraint(name, source);
					c.setCatalogName(catalogName);
					c.setSchemaName(schemaName);
					c.setTableName(tableName);
					convertConstraint(c);
					cols = list();
					tMap.put(catalogName, schemaName, name, c);
					colMap.put(catalogName, schemaName, name, cols);
					result.add(c);
				}
				cols.add(new Column(columnName));
			}
		});
		for (CheckConstraint c : result) {
			List<Column> cols = colMap.get(c.getCatalogName(),
					c.getSchemaName(), c.getName());
			if (cols.size() == 1) {
				c.addColumns(cols);
			}
		}
		return result;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("checkConstraints.sql");
	}

	private boolean isNotNullConstraint(String schemaName, String tableName,
			String columnName, String expression) {
		Pattern pattern = Pattern.compile(schemaName + "\\." + tableName
				+ "\\." + columnName + "[\\s]+IS[\\s]+NOT[\\s]+NULL");
		Matcher matcher = pattern.matcher(expression);
		return matcher.matches();
	}

	/**
	 * チェック制約の式のスキーマ名を除きます。
	 * 
	 * @param c
	 */
	private void convertConstraint(CheckConstraint c) {
		String convert = c.getExpression().replace(
				c.getSchemaName() + "." + c.getTableName() + ".",
				c.getTableName() + ".");
		c.setExpression(convert);
	}

}
