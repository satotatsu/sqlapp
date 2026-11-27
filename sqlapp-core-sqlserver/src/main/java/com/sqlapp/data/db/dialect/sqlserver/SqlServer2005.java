/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver;

import static com.sqlapp.util.CommonUtils.LEN_1GB;
import static com.sqlapp.util.CommonUtils.LEN_2GB;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlserver.metadata.SqlServer2005CatalogReader;
import com.sqlapp.data.db.dialect.sqlserver.sql.SqlServer2005SqlFactoryRegistry;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.properties.DataTypeLengthProperties;

/**
 * SQL Server2005
 * 
 * @author satoh
 * 
 */
public class SqlServer2005 extends SqlServer2000 {
	private static final String GENERATED_KEYS_TABLE = "#SQLAPP_GENERATED_KEYS";
	private static final Pattern VALUES_PATTERN = Pattern.compile("(?i)(?:DEFAULT\\s+)?VALUES\\b");
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -6574415406411255507L;

	@Override
	public boolean supportsBatchExecuteGeneratedKeysCapture() {
		return true;
	}

	@Override
	public void prepareBatchExecuteGeneratedKeys(Connection connection, Table table, Column identityColumn)
			throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute("IF OBJECT_ID('tempdb.." + GENERATED_KEYS_TABLE + "') IS NULL " + "CREATE TABLE "
					+ GENERATED_KEYS_TABLE + " ("
					+ "ROW_NO BIGINT IDENTITY(1,1) PRIMARY KEY, GENERATED_KEY SQL_VARIANT NOT NULL) "
					+ "ELSE TRUNCATE TABLE " + GENERATED_KEYS_TABLE);
		}
	}

	@Override
	public String handleBatchExecuteGeneratedKeysSql(Table table, Column identityColumn, String sql) {
		final int start = findValuesClause(sql);
		if (start < 0) {
			throw new IllegalArgumentException("Unable to add SQL Server generated-key capture to INSERT SQL: " + sql);
		}
		return sql.substring(0, start) + " OUTPUT INSERTED." + getObjectFullName(identityColumn.getName()) + " INTO "
				+ GENERATED_KEYS_TABLE + "(GENERATED_KEY) " + sql.substring(start);
	}

	/**
	 * 括弧の外にある最初のVALUES(またはDEFAULT VALUES)の位置を返します。 [識別子]、"識別子"、'文字列'とコメントの中は無視します。
	 * 
	 * @param sql INSERT SQL
	 * @return VALUES句の開始位置。見つからない場合は-1
	 */
	static int findValuesClause(final String sql) {
		final int length = sql.length();
		int depth = 0;
		int i = 0;
		while (i < length) {
			final char c = sql.charAt(i);
			if (c == '[') {
				i = skipQuoted(sql, i, ']');
				continue;
			} else if (c == '"' || c == '\'') {
				i = skipQuoted(sql, i, c);
				continue;
			} else if (c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
				final int end = sql.indexOf('\n', i);
				i = end < 0 ? length : end + 1;
				continue;
			} else if (c == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
				final int end = sql.indexOf("*/", i + 2);
				i = end < 0 ? length : end + 2;
				continue;
			} else if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
			} else if (depth == 0 && (c == 'V' || c == 'v' || c == 'D' || c == 'd')
					&& (i == 0 || !isIdentifierPart(sql.charAt(i - 1)))) {
				final Matcher matcher = VALUES_PATTERN.matcher(sql).region(i, length);
				if (matcher.lookingAt()) {
					return i;
				}
			}
			i++;
		}
		return -1;
	}

	private static int skipQuoted(final String sql, final int start, final char close) {
		int i = start + 1;
		while (i < sql.length()) {
			if (sql.charAt(i) == close) {
				if (i + 1 < sql.length() && sql.charAt(i + 1) == close) {
					i += 2;
					continue;
				}
				return i + 1;
			}
			i++;
		}
		return sql.length();
	}

	private static boolean isIdentifierPart(final char c) {
		return Character.isLetterOrDigit(c) || c == '_' || c == '@' || c == '#' || c == '$';
	}

	@Override
	public List<Object> getBatchExecuteGeneratedKeys(Connection connection, Table table, Column identityColumn)
			throws SQLException {
		List<Object> keys = new ArrayList<>();
		try (Statement statement = connection.createStatement();
				ResultSet resultSet = statement
						.executeQuery("SELECT GENERATED_KEY FROM " + GENERATED_KEYS_TABLE + " ORDER BY ROW_NO")) {
			while (resultSet.next()) {
				keys.add(resultSet.getObject(1));
			}
		}
		return keys;
	}

	protected SqlServer2005(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		super.registerDataType();
		// VARCHAR
		getDbDataTypes().addVarchar(LEN_2GB - 1, type -> {
			type.setPetternColumnTypeMatcher("VARCHAR\\s*\\(\\s*MAX\\s*\\)");
			type.setDefaultLength(LEN_2GB - 1).setCreateFormat("VARCHAR(MAX)").setFixedLength(false);
		});
		// NCHAR
		// NVARCHAR
		getDbDataTypes().addNVarchar(LEN_1GB - 1, type -> {
			type.setPetternColumnTypeMatcher("NVARCHAR\\s*\\(\\s*MAX\\s*\\)");
			type.setDefaultLength(LEN_1GB - 1).setCreateFormat("NVARCHAR(MAX)").setFixedLength(false);
		});
		// Binary
		getDbDataTypes().addVarBinary(LEN_2GB - 1, type -> {
			type.setPetternColumnTypeMatcher("(VARBINARY\\s*\\(\\s*MAX\\s*\\)|IMAGE)");
			type.setDefaultLength(LEN_2GB - 1).setCreateFormat("VARBINARY(MAX)").setFixedLength(false).setLiteral("0x",
					"");
		});
		// XML
		getDbDataTypes().addSqlXml("XML", type -> {
			type.setCreateFormat("XML");
			type.setLiteral("'", "'");
		});
	}

	@Override
	public boolean supportsWith() {
		return true;
	}

	/**
	 * WITHステートメント再帰のサポート
	 */
	@Override
	public boolean supportsWithRecursive() {
		return true;
	}

	/**
	 * TOP句のサポート
	 */
	@Override
	public boolean supportsTop() {
		return true;
	}

	@Override
	public boolean supportsRuleOnDelete(final CascadeRule rule) {
		return true;
	}

	@Override
	public boolean supportsRuleOnUpdate(final CascadeRule rule) {
		return true;
	}

	/**
	 * DBスキーマのサポート
	 */
	@Override
	public boolean supportsSchema() {
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getCatalogReader()
	 */
	@Override
	public CatalogReader getCatalogReader() {
		return new SqlServer2005CatalogReader(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.SqlServer2000#createOperationFactoryRegistry()
	 */
	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new SqlServer2005SqlFactoryRegistry(this);
	}

	@Override
	public boolean setDbType(final DataType dataType, final String productDataType, final Long lengthOrPrecision,
			final Integer scale, final DataTypeLengthProperties<?> column) {
		final boolean bool = super.setDbType(dataType, productDataType, lengthOrPrecision, scale, column);
		if (bool) {
			setVarcharMax(column);
		}
		return bool;
	}

	/**
	 * 最大長を超える(または-1の)VARCHAR/NVARCHAR/VARBINARYの長さを(MAX)の上限値にします。 VARCHAR(MAX)と
	 * VARBINARY(MAX)は2^31-1バイト、NVARCHAR(MAX)は2^30-1文字です。
	 */
	protected void setVarcharMax(final DataTypeLengthProperties<?> column) {
		if (column.getDataType() == DataType.VARCHAR) {
			if (column.getLength() != null
					&& (column.getLength().longValue() > 8000 || column.getLength().longValue() < 0)) {
				column.setLength(LEN_2GB - 1);
			}
		}
		if (column.getDataType() == DataType.NVARCHAR) {
			if (column.getLength() != null
					&& (column.getLength().longValue() > 4000 || column.getLength().longValue() < 0)) {
				column.setLength(LEN_1GB - 1);
			}
		}
		if (column.getDataType() == DataType.VARBINARY) {
			if (column.getLength() != null
					&& (column.getLength().longValue() > 8000 || column.getLength().longValue() < 0)) {
				column.setLength(LEN_2GB - 1);
			}
		}
	}
}
