/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-h2.
 *
 * sqlapp-core-h2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-h2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-h2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.h2.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TableLinkReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.TableLink;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.StringUtils;

/**
 * H2のテーブルリンク読み込みクラス
 * 
 * @author satoh
 * 
 */
public class H2TableLinkReader extends TableLinkReader {

	protected H2TableLinkReader(Dialect dialect) {
		super(dialect);
	}

	private static final Pattern tableLinkPattern = Pattern
			.compile(
					"CREATE\\s+(FORCE\\s+){0,1}LINKED\\s+TABLE\\s+[^(]+\\s*\\('(.*)'\\s*,\\s*'(.*)'\\s*,\\s*'(.*)'\\s*,\\s*'(.*)'\\s*,\\s*'(.*)'\\)",
					Pattern.CASE_INSENSITIVE + Pattern.MULTILINE
							+ Pattern.DOTALL);

	@Override
	protected List<TableLink> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<TableLink> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String table_Catalog = getString(rs, TABLE_CATALOG);
				String table_Schema = getString(rs, TABLE_SCHEMA);
				String table_Name = getString(rs, TABLE_NAME);
				String definition = getString(rs, "SQL");
				// long lastMod=rs.getLong("LAST_MODIFICATION");
				TableLink tableLink = new TableLink(table_Name);
				tableLink.setRemarks(getString(rs, REMARKS));
				tableLink.setCatalogName(table_Catalog);
				tableLink.setSchemaName(table_Schema);
				// tableLink.setCreated(toTimestamp(lastMod));
				Matcher matcher = tableLinkPattern.matcher(definition);
				if (matcher.matches()) {
					int i = 2;
					String driverClassName = StringUtils.getGroupString(
							matcher, i++);
					String host = StringUtils.getGroupString(matcher, i++);
					String userId = StringUtils.getGroupString(matcher, i++);
					String password = StringUtils.getGroupString(matcher, i++);
					String tableName = StringUtils.getGroupString(matcher, i++);
					tableLink.setDriverClassName(driverClassName);
					tableLink.setDataSource(host);
					tableLink.setUserId(userId);
					tableLink.setPassword(password);
					tableLink.setTableName(tableName);
				}
				result.add(tableLink);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("tableLinks.sql");
	}
}
