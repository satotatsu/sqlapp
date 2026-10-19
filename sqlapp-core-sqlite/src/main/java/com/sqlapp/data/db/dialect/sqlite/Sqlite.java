/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlite.
 *
 * sqlapp-core-sqlite is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlite is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlite.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlite;

import static com.sqlapp.util.CommonUtils.LEN_2GB;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlite.metadata.SqliteCatalogReader;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.dialect.sqlite.sql.SqliteSqlFactoryRegistry;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlSplitter;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.CorrelationStrategy;

/**
 * SQLite
 * 
 * @author satoh
 *
 */
public class Sqlite extends Dialect {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 3804986124610623903L;

	protected Sqlite(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		// VARCHAR
		getDbDataTypes().addVarchar("TEXT", LEN_2GB, type -> {
			type.setCreateFormat("TEXT");
		});
		getDbDataTypes().addChar("CHAR", LEN_2GB, type -> {
			type.setCreateFormat("TEXT");
		});
		// Binary
		getDbDataTypes().addBlob("BLOB", LEN_2GB, type -> {
			type.setCreateFormat("BLOB").setLiteral("X'", "'");
		});
		// Boolean
		getDbDataTypes().addBoolean();
		// TinyInt
		getDbDataTypes().addTinyInt("INTEGER", type -> {
		});
		// SmallInt
		getDbDataTypes().addSmallInt("INTEGER", type -> {
		});
		// Int
		getDbDataTypes().addInt("INTEGER", type -> {
		});
		// Int64
		getDbDataTypes().addBigInt("INTEGER", type -> {
		});
		// GUID
		getDbDataTypes().addUUID("BLOB");
		// Double
		getDbDataTypes().addDouble("REAL", type -> {
		});
		// Decimal
		getDbDataTypes().addNumeric(type -> {
		});
		getDbDataTypes().addDecimal(type -> {
		});
	}

	/**
	 * DB製品名
	 */
	@Override
	public CatalogReader getCatalogReader() {
		return new SqliteCatalogReader(this);
	}

	@Override
	public String getProductName() {
		return "SQLite";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getSimpleName()
	 */
	@Override
	public String getSimpleName() {
		return "sqlite";
	}

	/**
	 * TOP句のサポート
	 */
	@Override
	public boolean supportsTop() {
		return false;
	}

	@Override
	public boolean supportsLimit() {
		return true;
	}

	@Override
	public boolean supportsLimitOffset() {
		return true;
	}

	public boolean supportsViewColumnList() {
		return false;
	}

	public boolean supportsUpsert() {
		return false;
	}

	public boolean supportsRenameColumn() {
		return false;
	}

	public boolean supportsGeneratedColumns() {
		return false;
	}

	public boolean supportsDropColumn() {
		return false;
	}

	public boolean supportsStrictTables() {
		return false;
	}

	public boolean supportsWithoutRowid() {
		return false;
	}

	public boolean supportsPartialIndexes() {
		return false;
	}

	public boolean supportsExpressionIndexes() {
		return false;
	}

	@Override
	public int getMaxStatementParameterCount() {
		return 999;
	}

	@Override
	public String getIdentitySelectString() {
		return "select last_insert_rowid()";
	}

	@Override
	public boolean supportsIdentity() {
		return true;
	}

	@Override
	public boolean supportsInsertReturningResultSet() {
		return false;
	}

	@Override
	public String handleInsertReturningSql(final Table table, final Column identityColumn, final String sql) {
		if (!supportsInsertReturningResultSet())
			throw new IllegalArgumentException("SQLite INSERT RETURNING requires SQLite 3.35 or later");
		return sql + "\nRETURNING " + getObjectFullName(identityColumn.getName());
	}

	@Override
	public char getCloseQuote() {
		return ']';
	}

	@Override
	public char getOpenQuote() {
		return '[';
	}

	/**
	 * 現在日付の取得関数
	 */
	@Override
	public String getCurrentDateFunction() {
		return "CURRENT_DATE";
	}

	/**
	 * 現在日時の取得関数
	 */
	@Override
	public String getCurrentDateTimeFunction() {
		return "CURRENT_TIMESTAMP";
	}

	/**
	 * 現在日時(Timestamp)の取得関数
	 */
	@Override
	public String getCurrentTimestampFunction() {
		return "CURRENT_TIMESTAMP";
	}

	/**
	 * 現在日時(Timestamp)タイムゾーン付きの取得関数
	 */
	@Override
	public String getCurrentTimestampWithTimeZoneFunction() {
		return null;
	}

	/**
	 * 現在日時(Timestamp)タイムゾーン付きの取得関数
	 */
	@Override
	public String getCurrentTimeFunction() {
		return "CURRENT_TIME";
	}

	@Override
	public boolean supportsDropCascade() {
		return false;
	}

	@Override
	public boolean supportsCascadeDelete() {
		return true;
	}

	@Override
	public boolean supportsRuleOnDelete(CascadeRule rule) {
		return true;
	}

	@Override
	public boolean supportsCascadeUpdate() {
		return true;
	}

	@Override
	public boolean supportsRuleOnUpdate(CascadeRule rule) {
		return true;
	}

	@Override
	public boolean supportsDefaultValueFunction() {
		return false;
	}

	public String selectRecursiveSql(Table table, boolean backTrace) {
		return null;
	}

	@Override
	public int hashCode() {
		return getProductName().hashCode();
	}

	/**
	 * 同値判定
	 */
	@Override
	public boolean equals(Object obj) {
		if (!super.equals(obj)) {
			return false;
		}
		return true;
	}

	@Override
	public SqliteSqlSplitter createSqlSplitter() {
		return new SqliteSqlSplitter(this);
	}

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new SqliteSqlFactoryRegistry(this);
	}

	@Override
	public SqliteSqlBuilder createSqlBuilder() {
		return new SqliteSqlBuilder(this);
	}

	@Override
	public boolean supportsValues() {
		return true;
	}

	@Override
	public CorrelationStrategy getCorrelationStrategy() {
		return CorrelationStrategy.BY_SOURCE_ROWID;
	}

	@Override
	public boolean supportsRowValueComparison() {
		return false;
	}

	@Override
	public boolean supportsRowValueComparisonIn() {
		return false;
	}
}
