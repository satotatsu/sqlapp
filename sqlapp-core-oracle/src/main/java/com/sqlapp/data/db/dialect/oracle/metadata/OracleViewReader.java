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

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.ExcludeConstraintReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.db.metadata.ViewReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Oracleのビュー作成クラス
 * 
 * @author satoh
 * 
 */
public class OracleViewReader extends ViewReader {

	protected OracleViewReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Table> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Table> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				View table = createTable(rs);
				result.add(table);
			}
		});
		return result;
	}

	protected View createTable(ExResultSet rs) throws SQLException {
		String text = getString(rs, "TEXT");
		View table = createTable(getString(rs, "VIEW_NAME"));
		table.setSchemaName(getString(rs, "OWNER"));
		table.setStatement(text);
		String check = getString(rs, "ORACLE_CHECK_CONSTRAINT"),
				readOnly = getString(rs, "ORACLE_READ_ONLY_CONSTRAINT");
		if (check != null || readOnly != null) {
			table.setStatement(withoutRestriction(text, readOnly != null));
		}
		if (check != null)
			table.getSpecifics().put(com.sqlapp.data.db.dialect.oracle.sql.OracleCreateViewFactory.CHECK_CONSTRAINT,
					check);
		if (readOnly != null)
			table.getSpecifics().put(com.sqlapp.data.db.dialect.oracle.sql.OracleCreateViewFactory.READ_ONLY_CONSTRAINT,
					readOnly);
		OracleMetadataUtils.setCommonInfo(rs, table);
		return table;
	}

	/** ALL_VIEWS.TEXT includes the restriction, but omits its constraint name. */
	static String withoutRestriction(String text, boolean readOnly) {
		if (text == null)
			return null;
		char[] code = text.toCharArray();
		for (int i = 0; i < text.length();) {
			int start = i, end = i;
			char c = text.charAt(i);
			if (text.startsWith("--", i)) {
				end = text.indexOf('\n', i + 2);
				if (end < 0)
					end = text.length();
			} else if (text.startsWith("/*", i)) {
				end = text.indexOf("*/", i + 2);
				end = end < 0 ? text.length() : end + 2;
			} else if ((c == 'q' || c == 'Q') && i + 2 < text.length() && text.charAt(i + 1) == '\'') {
				char close = switch (text.charAt(i + 2)) {
				case '[' -> ']';
				case '(' -> ')';
				case '{' -> '}';
				case '<' -> '>';
				default -> text.charAt(i + 2);
				};
				end = text.indexOf("" + close + '\'', i + 3);
				end = end < 0 ? text.length() : end + 2;
			} else if (c == '\'' || c == '"') {
				end = i + 1;
				while (end < text.length()) {
					if (text.charAt(end++) == c) {
						if (end < text.length() && text.charAt(end) == c)
							end++;
						else
							break;
					}
				}
			}
			if (end == i) {
				i++;
				continue;
			}
			java.util.Arrays.fill(code, start, end, ' ');
			i = end;
		}
		var suffix = java.util.regex.Pattern
				.compile("(?is)\\bWITH\\s+" + (readOnly ? "READ\\s+ONLY" : "CHECK\\s+OPTION") + "\\b(?=\\s*$)")
				.matcher(new String(code));
		if (!suffix.find())
			return text;
		return text.substring(0, suffix.start()).stripTrailing() + text.substring(suffix.end());
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("views.sql");
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new OracleColumnReader(this.getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.metadata.TableReader#newExcludeConstraintReader ()
	 */
	@Override
	protected ExcludeConstraintReader newExcludeConstraintReader() {
		return null;
	}
}
